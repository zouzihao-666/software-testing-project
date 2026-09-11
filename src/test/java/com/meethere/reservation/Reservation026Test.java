package com.meethere.reservation;

import com.meethere.dao.OrderDao;
import com.meethere.dao.UserDao;
import com.meethere.dao.VenueDao;
import com.meethere.entity.Order;
import com.meethere.entity.User;
import com.meethere.entity.Venue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation026Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-026 预约订单审核通过")
    void shouldChangePendingOrderToApproved() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        User admin = findOrCreateAdmin();
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID(), 16, 18);
        assertNotNull(day, "未来30天内没有可用日期");
        Order pending = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(16, 0), 2);
        int orderId = pending.getOrderID();

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            loginAdmin(driver, wait, admin);
            driver.get("http://localhost:8888/reservation_manage");
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href='#tab2']"))).click();
            WebElement pass = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                    "//a[contains(@onclick,'pass(" + orderId + ",this)')]")));
            Thread.sleep(2500);
            pass.click();
            Alert confirm = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("确定通过订单？", confirm.getText());
            confirm.accept();
            Alert success = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("通过成功！", success.getText());
            success.accept();
            Thread.sleep(2500);

            Order approved = orderDao.findByOrderID(orderId);
            assertNotNull(approved);
            assertEquals(2, approved.getState(), "审核后状态应为已审核");
            assertEquals(pending.getUserID(), approved.getUserID());
            assertEquals(pending.getVenueID(), approved.getVenueID());
            assertEquals(pending.getStartTime(), approved.getStartTime());
            assertEquals(pending.getHours(), approved.getHours());
            assertEquals(pending.getTotal(), approved.getTotal());

            driver.get("http://localhost:8888/quit.do");
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_manage");
            WebElement item = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                    "//li[contains(.,'" + day + "') and contains(.,'16:00')]")));
            Thread.sleep(3000);
            String text = item.getText();
            assertTrue(text.contains("篮球馆"));
            assertTrue(text.contains("2") && text.contains("400"));
            assertTrue(!text.contains("审核中"), "审核通过后不应继续显示审核中");
            System.out.printf("订单审核通过：orderID=%d，管理员=%s，日期=%s%n",
                    orderId, admin.getUserID(), day);
        } finally {
            driver.quit();
        }
    }

    private User findOrCreateAdmin() {
        User existing = userDao.findAll().stream()
                .filter(user -> user.getIsadmin() == 1).findFirst().orElse(null);
        if (existing != null) return existing;
        User admin = new User();
        admin.setUserID("test_admin");
        admin.setUserName("测试管理员");
        admin.setPassword("TestAdmin01!");
        admin.setEmail("test_admin@meethere.local");
        admin.setPhone("13000000000");
        admin.setPicture("");
        admin.setIsadmin(1);
        return userDao.saveAndFlush(admin);
    }

    private void loginAdmin(WebDriver driver, WebDriverWait wait, User admin) {
        driver.get("http://localhost:8888/login");
        driver.findElement(By.id("userID")).sendKeys(admin.getUserID());
        driver.findElement(By.id("password")).sendKeys(admin.getPassword());
        driver.findElement(By.id("submit")).click();
        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        assertEquals("登录成功！", alert.getText());
        alert.accept();
        wait.until(ExpectedConditions.urlMatches(".*/admin_index$"));
    }
}
