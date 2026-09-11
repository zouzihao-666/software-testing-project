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
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation001Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-001 正常预约篮球馆")
    void shouldSubmitTwoHourBasketballReservation() throws Exception {
        User user = prepareUser();
        assertEquals(0, user.getIsadmin(), "user01必须是普通用户");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        assertEquals(200, venue.getPrice(), "篮球馆须为200元/小时");
        assertFalse(LocalTime.parse(venue.getOpen_time()).isAfter(LocalTime.of(9, 0)));
        assertFalse(LocalTime.parse(venue.getClose_time()).isBefore(LocalTime.of(11, 0)));
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID()
                        || user.getUserID().equals(order.getUserID()), 9, 11);
        assertNotNull(day, "未来30天内没有空闲测试日期");

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());
            ReservationTestSupport.selectDate(driver, wait, day);
            wait.until(webDriver -> !driver.findElement(By.id("9"))
                    .getAttribute("class").contains("occupied"));
            ReservationTestSupport.drag(driver, "9", 55);
            assertEquals("2", driver.findElement(By.id("hours")).getAttribute("value"));
            Thread.sleep(2500);
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("提交成功！", alert.getText());
            alert.accept();
            wait.until(ExpectedConditions.urlMatches(".*/order_manage$"));

            List<Order> saved = orderDao.findByVenueIDAndStartTimeIsBetween(
                            venue.getVenueID(), day.atTime(9, 0), day.atTime(9, 0)).stream()
                    .filter(order -> user.getUserID().equals(order.getUserID()))
                    .collect(Collectors.toList());
            assertEquals(1, saved.size());
            Order order = saved.get(0);
            assertAll("核对订单",
                    () -> assertTrue(order.getOrderID() > 0),
                    () -> assertEquals(2, order.getHours()),
                    () -> assertEquals(1, order.getState()),
                    () -> assertEquals(400, order.getTotal()),
                    () -> assertNotNull(order.getOrderTime()));
            System.out.printf("预约成功：orderID=%d，日期=%s，09:00—11:00%n",
                    order.getOrderID(), day);
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }

    private User prepareUser() {
        User existing = userDao.findByUserID("user01");
        if (existing != null) return existing;
        User user = new User();
        user.setUserID("user01");
        user.setUserName("预约测试用户");
        user.setPassword("TestUser01!");
        user.setEmail("user01@example.com");
        user.setPhone("");
        user.setPicture("");
        user.setIsadmin(0);
        return userDao.saveAndFlush(user);
    }
}
