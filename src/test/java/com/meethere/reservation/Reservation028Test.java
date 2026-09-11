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
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation028Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-028 修改到已占用时段")
    void shouldRejectModificationToOccupiedTime() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID(), 9, 17);
        assertNotNull(day, "未来30天内没有可用日期");
        Order original = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(9, 0), 1);
        Order occupied = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(14, 0), 2);
        occupied.setUserID("RES-H-028-other-user");
        orderDao.saveAndFlush(occupied);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_manage");
            WebElement modify = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(
                    "a[href='modifyOrder.do?orderID=" + original.getOrderID() + "']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", modify);
            wait.until(ExpectedConditions.urlContains("modifyOrder.do?orderID=" + original.getOrderID()));
            ReservationTestSupport.selectDate(driver, wait, day);
            wait.until(webDriver -> driver.findElement(By.id("15"))
                    .getAttribute("class").contains("occupied"));
            Thread.sleep(2500);
            ReservationTestSupport.drag(driver, "15", 55);
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            String message = alert.getText();
            alert.accept();
            Thread.sleep(2500);

            Order after = orderDao.findByOrderID(original.getOrderID());
            assertNotNull(after);
            if (!after.getStartTime().equals(day.atTime(9, 0)) || after.getHours() != 1) {
                fail("修改15:00—17:00时系统跳过已占用部分并改变了原订单，页面提示："
                        + message + "；原订单编号：" + original.getOrderID()
                        + "，实际时间：" + after.getStartTime() + "，时长：" + after.getHours());
            }
            assertEquals(1, after.getState());
            assertEquals(200, after.getTotal());
            System.out.printf("冲突修改已拦截：原订单=%d，占用订单=%d，日期=%s%n",
                    original.getOrderID(), occupied.getOrderID(), day);
        } finally {
            driver.quit();
        }
    }
}
