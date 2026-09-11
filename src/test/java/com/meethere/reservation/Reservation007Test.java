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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation007Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-007 预约过去日期")
    void shouldRejectPastReservationDate() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertEquals(0, user.getIsadmin(), "user01必须是普通用户");

        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startTime = yesterday.atTime(9, 0);
        Set<Integer> existingOrderIds = matchingOrderIds(user, venue, startTime);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());

            WebElement dateInput = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("date")));
            dateInput.sendKeys(Keys.CONTROL, "a");
            dateInput.sendKeys(yesterday.toString());
            dateInput.sendKeys(Keys.TAB);
            Thread.sleep(2000);

            if (!yesterday.toString().equals(dateInput.getAttribute("value"))) {
                assertEquals(existingOrderIds, matchingOrderIds(user, venue, startTime),
                        "过去日期不可选择时不能生成订单");
                Thread.sleep(3000);
                return;
            }

            WebElement nineOClock = wait.until(ExpectedConditions.elementToBeClickable(By.id("9")));
            new Actions(driver)
                    .moveToElement(nineOClock)
                    .clickAndHold()
                    .moveByOffset(1, 0)
                    .release()
                    .perform();

            String selectedStartTime = driver.findElement(By.id("startTime")).getAttribute("value");
            if (selectedStartTime.isEmpty()) {
                assertEquals(existingOrderIds, matchingOrderIds(user, venue, startTime),
                        "过去时段不可选择时不能生成订单");
                Thread.sleep(3000);
                return;
            }
            assertTrue(selectedStartTime.endsWith("09:00"), "开始时间应为09:00");
            assertEquals("1", driver.findElement(By.id("hours")).getAttribute("value"),
                    "预约时长应为1小时");

            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
            Thread.sleep(2500);

            Set<Integer> currentOrderIds = matchingOrderIds(user, venue, startTime);
            Set<Integer> newOrderIds = new HashSet<>(currentOrderIds);
            newOrderIds.removeAll(existingOrderIds);
            if (!newOrderIds.isEmpty()) {
                List<Order> invalidOrders = orderDao.findAllById(newOrderIds);
                orderDao.deleteAll(invalidOrders);
                orderDao.flush();
                Thread.sleep(3000);
                fail("系统允许提交过去日期的预约");
            }

            assertEquals(existingOrderIds, currentOrderIds, "提交过去日期时不能生成订单");
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }

    private Set<Integer> matchingOrderIds(User user, Venue venue, LocalDateTime startTime) {
        return orderDao.findByVenueIDAndStartTimeIsBetween(
                        venue.getVenueID(), startTime, startTime)
                .stream()
                .filter(order -> user.getUserID().equals(order.getUserID()))
                .map(Order::getOrderID)
                .collect(Collectors.toSet());
    }

    private void login(WebDriver driver, WebDriverWait wait, User user) throws InterruptedException {
        driver.get("http://localhost:8888/login");
        driver.findElement(By.id("userID")).sendKeys(user.getUserID());
        driver.findElement(By.id("password")).sendKeys(user.getPassword());
        driver.findElement(By.id("submit")).click();

        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        assertEquals("登录成功！", alert.getText());
        Thread.sleep(1500);
        alert.accept();
        wait.until(ExpectedConditions.urlMatches(".*/index$"));
    }
}
