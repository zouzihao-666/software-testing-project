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
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation017Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-017 预约时长为0")
    void shouldRejectZeroHourReservation() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");

        LocalDate day = findDayWithoutVenueOrders(orderDao.findAll(), venue);
        assertNotNull(day, "未来30天内没有符合条件的测试日期");
        LocalDateTime startTime = day.atTime(10, 0);
        Set<Integer> existingOrderIds = matchingOrderIds(user, venue, startTime);

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

            WebElement tenOClock = wait.until(ExpectedConditions.elementToBeClickable(By.id("10")));
            wait.until(webDriver -> !tenOClock.getAttribute("class").contains("occupied")
                    && !tenOClock.getAttribute("class").contains("banned"));
            new Actions(driver).moveToElement(tenOClock).clickAndHold()
                    .moveByOffset(1, 0).release().perform();
            assertEquals("1", driver.findElement(By.id("hours")).getAttribute("value"));

            JavascriptExecutor javascript = (JavascriptExecutor) driver;
            javascript.executeScript("document.getElementById('hours').value='0';");
            assertEquals("0", driver.findElement(By.id("hours")).getAttribute("value"));
            Thread.sleep(2500);

            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            String message = alert.getText();
            alert.accept();
            Thread.sleep(2500);

            Set<Integer> currentOrderIds = matchingOrderIds(user, venue, startTime);
            Set<Integer> newOrderIds = new HashSet<>(currentOrderIds);
            newOrderIds.removeAll(existingOrderIds);
            if (!newOrderIds.isEmpty()) {
                List<Order> invalidOrders = orderDao.findAllById(newOrderIds);
                boolean hasZeroHourOrder = invalidOrders.stream()
                        .anyMatch(order -> order.getHours() == 0);
                if (hasZeroHourOrder) {
                    fail("后台接受hours=0并生成了0小时订单，页面提示：" + message
                            + "；异常订单编号：" + newOrderIds);
                }
                fail("后台使用异常时长生成了订单；异常订单编号：" + newOrderIds);
            }

            assertEquals(existingOrderIds, currentOrderIds, "hours=0时不能生成订单");
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }

    private Set<Integer> matchingOrderIds(User user, Venue venue, LocalDateTime startTime) {
        return orderDao.findByVenueIDAndStartTimeIsBetween(
                        venue.getVenueID(), startTime, startTime)
                .stream().filter(order -> user.getUserID().equals(order.getUserID()))
                .map(Order::getOrderID).collect(Collectors.toSet());
    }

    private LocalDate findDayWithoutVenueOrders(List<Order> orders, Venue venue) {
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate candidate = LocalDate.now().plusDays(offset);
            LocalDateTime dayStart = candidate.atStartOfDay();
            LocalDateTime dayEnd = candidate.plusDays(1).atStartOfDay();
            boolean occupied = orders.stream().anyMatch(order ->
                    order.getVenueID() == venue.getVenueID() && order.getStartTime() != null
                            && !order.getStartTime().isBefore(dayStart)
                            && order.getStartTime().isBefore(dayEnd));
            if (!occupied) {
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
