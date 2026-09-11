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
class Reservation022Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-022 预约与已有时段首尾相邻")
    void shouldAllowAdjacentReservation() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID(), 10, 13);
        assertNotNull(day, "未来30天内没有可用日期");
        Order original = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(10, 0), 2);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());
            ReservationTestSupport.selectDate(driver, wait, day);
            wait.until(webDriver -> driver.findElement(By.id("11"))
                    .getAttribute("class").contains("occupied"));
            wait.until(webDriver -> !driver.findElement(By.id("12"))
                    .getAttribute("class").contains("occupied"));
            ReservationTestSupport.drag(driver, "12", 1);
            assertEquals("1", driver.findElement(By.id("hours")).getAttribute("value"));
            Thread.sleep(2500);
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("提交成功！", alert.getText());
            alert.accept();
            wait.until(ExpectedConditions.urlMatches(".*/order_manage$"));

            LocalDateTime adjacentStart = day.atTime(12, 0);
            List<Order> adjacent = orderDao.findByVenueIDAndStartTimeIsBetween(
                    venue.getVenueID(), adjacentStart, adjacentStart);
            Order saved = adjacent.stream().filter(order ->
                    user.getUserID().equals(order.getUserID())).findFirst().orElse(null);
            assertNotNull(saved, "相邻时段订单未生成");
            assertEquals(1, saved.getHours());
            assertEquals(200, saved.getTotal());
            System.out.printf("相邻预约成功：原订单=%d，新订单=%d，日期=%s%n",
                    original.getOrderID(), saved.getOrderID(), day);
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }
}
