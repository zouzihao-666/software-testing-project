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
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation016Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-016 开放时间内最长预约")
    void shouldSubmitMaximumReservationWithinOpeningHours() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        assertEquals(LocalTime.of(8, 0), LocalTime.parse(venue.getOpen_time()));
        assertEquals(LocalTime.of(21, 0), LocalTime.parse(venue.getClose_time()));
        assertEquals(200, venue.getPrice());

        LocalDate day = findAvailableFullDay(orderDao.findAll(), user, venue);
        assertNotNull(day, "未来30天内没有全天空闲的测试日期");
        LocalDateTime startTime = day.atTime(8, 0);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());

            WebElement dateInput = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("date")));
            dateInput.sendKeys(Keys.CONTROL, "a");
            dateInput.sendKeys(day.toString());
            dateInput.sendKeys(Keys.TAB);

            WebElement openingHour = wait.until(ExpectedConditions.elementToBeClickable(By.id("8")));
            WebElement lastHour = driver.findElement(By.id("20"));
            wait.until(webDriver -> !openingHour.getAttribute("class").contains("banned")
                    && !lastHour.getAttribute("class").contains("banned")
                    && !openingHour.getAttribute("class").contains("occupied")
                    && !lastHour.getAttribute("class").contains("occupied"));
            new Actions(driver).moveToElement(openingHour).clickAndHold()
                    .moveByOffset(600, 0).release().perform();

            assertTrue(driver.findElement(By.id("startTime")).getAttribute("value")
                    .endsWith("08:00"));
            assertEquals("13", driver.findElement(By.id("hours")).getAttribute("value"),
                    "完整开放时段应为13小时");
            Thread.sleep(2500);

            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("提交成功！", alert.getText());
            alert.accept();
            wait.until(ExpectedConditions.urlMatches(".*/order_manage$"));

            List<Order> savedOrders = orderDao
                    .findByVenueIDAndStartTimeIsBetween(venue.getVenueID(), startTime, startTime)
                    .stream().filter(order -> user.getUserID().equals(order.getUserID()))
                    .collect(Collectors.toList());
            assertEquals(1, savedOrders.size());
            Order savedOrder = savedOrders.get(0);
            assertEquals(13, savedOrder.getHours());
            assertEquals(startTime.plusHours(13),
                    savedOrder.getStartTime().plusHours(savedOrder.getHours()));
            assertEquals(2600, savedOrder.getTotal());
            assertEquals(1, savedOrder.getState(), "订单状态应为未审核");
            System.out.printf("预约成功：orderID=%d，日期=%s，08:00—21:00，共13小时%n",
                    savedOrder.getOrderID(), day);
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }

    private LocalDate findAvailableFullDay(List<Order> orders, User user, Venue venue) {
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate candidate = LocalDate.now().plusDays(offset);
            LocalDateTime start = candidate.atTime(8, 0);
            LocalDateTime end = candidate.atTime(21, 0);
            boolean conflict = orders.stream().anyMatch(order -> order.getStartTime() != null
                    && order.getStartTime().isBefore(end)
                    && order.getStartTime().plusHours(order.getHours()).isAfter(start)
                    && (order.getVenueID() == venue.getVenueID()
                    || user.getUserID().equals(order.getUserID())));
            if (!conflict) {
                return candidate;
            }
        }
        return null;
    }

    private void login(WebDriver driver, WebDriverWait wait, User user) {
        driver.get("http://localhost:8888/login");
        driver.findElement(By.id("userID")).sendKeys(user.getUserID());
        driver.findElement(By.id("password")).sendKeys(user.getPassword());
        driver.findElement(By.id("submit")).click();
        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        assertEquals("登录成功！", alert.getText());
        alert.accept();
        wait.until(ExpectedConditions.urlMatches(".*/index$"));
    }
}
