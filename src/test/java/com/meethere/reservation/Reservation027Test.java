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
import org.openqa.selenium.JavascriptExecutor;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation027Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-027 修改未审核预约")
    void shouldModifyPendingReservation() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID(), 9, 16);
        assertNotNull(day, "未来30天内没有可用日期");
        Order order = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(9, 0), 1);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_manage");
            WebElement modify = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(
                    "a[href='modifyOrder.do?orderID=" + order.getOrderID() + "']")));
            Thread.sleep(2500);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", modify);
            wait.until(ExpectedConditions.urlContains("modifyOrder.do?orderID=" + order.getOrderID()));

            ReservationTestSupport.selectDate(driver, wait, day);
            wait.until(webDriver -> !driver.findElement(By.id("14"))
                    .getAttribute("class").contains("occupied"));
            ReservationTestSupport.drag(driver, "14", 55);
            assertEquals("2", driver.findElement(By.id("hours")).getAttribute("value"));
            Thread.sleep(2500);
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("修改成功！", alert.getText());
            alert.accept();
            wait.until(ExpectedConditions.urlMatches(".*/order_manage$"));

            Order modified = orderDao.findByOrderID(order.getOrderID());
            assertNotNull(modified);
            assertEquals(day.atTime(14, 0), modified.getStartTime());
            assertEquals(2, modified.getHours());
            assertEquals(400, modified.getTotal());
            assertEquals(1, modified.getState(), "修改后应重新处于未审核状态");
            System.out.printf("订单修改成功：orderID=%d，日期=%s，14:00—16:00%n",
                    modified.getOrderID(), day);
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }
}
