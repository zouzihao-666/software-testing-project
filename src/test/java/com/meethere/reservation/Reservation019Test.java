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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation019Test {

    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-019 预约时长超过开放时长")
    void shouldRejectReservationLongerThanOpeningHours() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = findEmptyDay(orderDao.findAll(), venue);
        assertNotNull(day, "未来30天内没有全天空闲日期");
        LocalDateTime startTime = day.atTime(8, 0);
        Set<Integer> oldIds = matchingIds(user, venue, startTime);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());
            WebElement date = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("date")));
            date.sendKeys(Keys.CONTROL, "a");
            date.sendKeys(day.toString());
            date.sendKeys(Keys.TAB);
            WebElement opening = wait.until(ExpectedConditions.elementToBeClickable(By.id("8")));
            new Actions(driver).moveToElement(opening).clickAndHold().moveByOffset(1, 0).release().perform();
            ((JavascriptExecutor) driver).executeScript(
                    "document.getElementById('hours').value='14';");
            assertEquals("14", driver.findElement(By.id("hours")).getAttribute("value"));
            Thread.sleep(2500);

            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            String message = alert.getText();
            alert.accept();
            Thread.sleep(2500);

            Set<Integer> newIds = matchingIds(user, venue, startTime);
            newIds.removeAll(oldIds);
            if (!newIds.isEmpty()) {
                List<Order> orders = orderDao.findAllById(newIds);
                if (orders.stream().anyMatch(order -> order.getHours() == 14)) {
                    fail("系统生成了超过开放时长的订单，页面提示：" + message
                            + "；异常订单编号：" + newIds);
                }
                fail("系统使用异常时长生成了订单；异常订单编号：" + newIds);
            }
            assertEquals(oldIds, matchingIds(user, venue, startTime),
                    "08:00开始预约14小时不能生成订单");
        } finally {
            driver.quit();
        }
    }

    private Set<Integer> matchingIds(User user, Venue venue, LocalDateTime startTime) {
        return orderDao.findByVenueIDAndStartTimeIsBetween(
                        venue.getVenueID(), startTime, startTime).stream()
                .filter(order -> user.getUserID().equals(order.getUserID()))
                .map(Order::getOrderID).collect(Collectors.toSet());
    }

    private LocalDate findEmptyDay(List<Order> orders, Venue venue) {
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate day = LocalDate.now().plusDays(offset);
            boolean occupied = orders.stream().anyMatch(order -> order.getStartTime() != null
                    && order.getVenueID() == venue.getVenueID()
                    && order.getStartTime().toLocalDate().equals(day));
            if (!occupied) return day;
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
