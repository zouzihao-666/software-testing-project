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
class Reservation014Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-014 预约时段超过闭馆时间")
    void shouldRejectReservationExceedingClosingTime() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        assertEquals(LocalTime.of(21, 0), LocalTime.parse(venue.getClose_time()));

        LocalDate day = findDayWithoutVenueOrders(orderDao.findAll(), venue);
        assertNotNull(day, "未来30天内没有符合条件的测试日期");
        LocalDateTime startTime = day.atTime(20, 0);
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

            WebElement twentyOClock = wait.until(ExpectedConditions.elementToBeClickable(By.id("20")));
            WebElement closingTime = driver.findElement(By.id("21"));
            wait.until(webDriver -> closingTime.getAttribute("class").contains("banned"));
            new Actions(driver)
                    .moveToElement(twentyOClock)
                    .clickAndHold()
                    .moveByOffset(55, 0)
                    .release()
                    .perform();
            Thread.sleep(2000);

            String selectedHours = driver.findElement(By.id("hours")).getAttribute("value");
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            String message = alert.getText();
            Thread.sleep(2000);
            alert.accept();
            Thread.sleep(2500);

            Set<Integer> currentOrderIds = matchingOrderIds(user, venue, startTime);
            Set<Integer> newOrderIds = new HashSet<>(currentOrderIds);
            newOrderIds.removeAll(existingOrderIds);
            if (!newOrderIds.isEmpty()) {
                fail("选择20:00—22:00后被缩短为" + selectedHours
                        + "小时并成功提交，系统未提示超出开放时间；异常订单编号：" + newOrderIds);
            }

            assertTrue(!"提交成功！".equals(message) || selectedHours.isEmpty(),
                    "超过闭馆时间的预约应被拒绝");
            assertEquals(existingOrderIds, currentOrderIds, "不能生成超过闭馆时间的订单");
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
