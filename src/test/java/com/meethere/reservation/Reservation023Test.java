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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation023Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-023 不同场馆相同时段预约")
    void shouldAllowSameTimeAtDifferentVenues() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue basketball = venueDao.findByVenueName("篮球馆");
        Venue badminton = venueDao.findByVenueName("羽毛球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(basketball, "请先添加篮球馆");
        assertNotNull(badminton, "请先添加羽毛球馆");
        assertEquals(100, badminton.getPrice(), "羽毛球馆单价应为100元");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == basketball.getVenueID()
                        || order.getVenueID() == badminton.getVenueID(), 10, 12);
        assertNotNull(day, "未来30天内没有两个场馆同时空闲的日期");
        Order original = ReservationTestSupport.saveOrder(
                orderDao, user, basketball, day.atTime(10, 0), 2);
        original.setUserID("RES-H-023-other-user");
        orderDao.saveAndFlush(original);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + badminton.getVenueID());
            ReservationTestSupport.selectDate(driver, wait, day);
            wait.until(webDriver -> !driver.findElement(By.id("10"))
                    .getAttribute("class").contains("occupied"));
            ReservationTestSupport.drag(driver, "10", 55);
            assertEquals("2", driver.findElement(By.id("hours")).getAttribute("value"));
            Thread.sleep(2500);
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("提交成功！", alert.getText());
            alert.accept();
            wait.until(ExpectedConditions.urlMatches(".*/order_manage$"));

            LocalDateTime start = day.atTime(10, 0);
            List<Order> orders = orderDao.findByVenueIDAndStartTimeIsBetween(
                    badminton.getVenueID(), start, start);
            Order saved = orders.stream().filter(order -> user.getUserID().equals(order.getUserID()))
                    .findFirst().orElse(null);
            assertNotNull(saved, "羽毛球馆订单未生成");
            assertEquals(2, saved.getHours());
            assertEquals(200, saved.getTotal());
            System.out.printf("不同场馆同时预约成功：篮球馆订单=%d，羽毛球馆订单=%d，日期=%s%n",
                    original.getOrderID(), saved.getOrderID(), day);
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }
}
