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
class Reservation012Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-012 结束时间等于闭馆时间")
    void shouldSubmitReservationEndingAtClosingTime() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        assertEquals(LocalTime.of(21, 0), LocalTime.parse(venue.getClose_time()),
                "篮球馆闭馆时间应为21:00");
        assertEquals(200, venue.getPrice(), "篮球馆须为200元/小时");

        List<Order> existingOrders = orderDao.findAll();
        LocalDate day = findDayWithoutVenueOrders(existingOrders, user, venue);
        assertNotNull(day, "未来30天内没有符合条件的测试日期");
        LocalDateTime startTime = day.atTime(20, 0);

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

            WebElement lastAvailableHour = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id("20")));
            wait.until(webDriver -> !lastAvailableHour.getAttribute("class").contains("occupied")
                    && !lastAvailableHour.getAttribute("class").contains("banned"));
            new Actions(driver)
                    .moveToElement(lastAvailableHour)
                    .clickAndHold()
                    .moveByOffset(1, 0)
                    .release()
                    .perform();

            assertTrue(driver.findElement(By.id("startTime")).getAttribute("value")
                    .endsWith("20:00"), "开始时间应为20:00");
            assertEquals("1", driver.findElement(By.id("hours")).getAttribute("value"));
            Thread.sleep(2000);

            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("提交成功！", alert.getText());
            Thread.sleep(2000);
            alert.accept();
            wait.until(ExpectedConditions.urlMatches(".*/order_manage$"));

            List<Order> savedOrders = orderDao
                    .findByVenueIDAndStartTimeIsBetween(venue.getVenueID(), startTime, startTime)
                    .stream()
                    .filter(order -> user.getUserID().equals(order.getUserID()))
                    .collect(Collectors.toList());
            assertEquals(1, savedOrders.size(), "数据库中应有一笔对应的新订单");
            Order savedOrder = savedOrders.get(0);
            assertEquals(startTime.plusHours(1), savedOrder.getStartTime().plusHours(savedOrder.getHours()),
                    "结束时间应为21:00");
            assertEquals(1, savedOrder.getState(), "订单状态应为未审核");
            assertEquals(200, savedOrder.getTotal());
            System.out.printf("预约成功：orderID=%d，日期=%s，20:00—21:00%n",
                    savedOrder.getOrderID(), day);
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }

    private LocalDate findDayWithoutVenueOrders(List<Order> orders, User user, Venue venue) {
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate candidate = LocalDate.now().plusDays(offset);
            LocalDateTime dayStart = candidate.atStartOfDay();
            LocalDateTime dayEnd = candidate.plusDays(1).atStartOfDay();
            LocalDateTime start = candidate.atTime(20, 0);
            LocalDateTime end = start.plusHours(1);
            boolean venueHasOrder = orders.stream().anyMatch(order ->
                    order.getVenueID() == venue.getVenueID() && order.getStartTime() != null
                            && !order.getStartTime().isBefore(dayStart)
                            && order.getStartTime().isBefore(dayEnd));
            boolean userConflict = orders.stream().anyMatch(order ->
                    user.getUserID().equals(order.getUserID()) && order.getStartTime() != null
                            && order.getStartTime().isBefore(end)
                            && order.getStartTime().plusHours(order.getHours()).isAfter(start));
            if (!venueHasOrder && !userConflict) {
                return candidate;
            }
        }
        return null;
    }

    private void login(WebDriver driver, WebDriverWait wait, User user) throws InterruptedException {
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
