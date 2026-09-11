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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation020Test {

    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-020 完全重复预约")
    void shouldRejectExactlyDuplicatedReservation() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = findAvailableDay(orderDao.findAll(), venue, 9, 11);
        assertNotNull(day, "未来30天内没有可准备重复预约的日期");
        LocalDateTime startTime = day.atTime(9, 0);
        Order existingOrder = saveOrder(user, venue, startTime, 2);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());
            WebElement date = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("date")));
            date.sendKeys(Keys.CONTROL, "a");
            date.sendKeys(day.toString());
            date.sendKeys(Keys.TAB);

            WebElement nine = driver.findElement(By.id("9"));
            WebElement ten = driver.findElement(By.id("10"));
            wait.until(webDriver -> nine.getAttribute("class").contains("occupied")
                    && ten.getAttribute("class").contains("occupied"));
            Thread.sleep(2500);
            new Actions(driver).moveToElement(nine).clickAndHold()
                    .moveByOffset(55, 0).release().perform();
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("您尚未选择预约时间段！", alert.getText());
            Thread.sleep(2500);
            alert.accept();

            List<Order> orders = matchingOrders(user, venue, startTime);
            assertEquals(1, orders.size(), "相同时段不能出现第二笔订单");
            assertEquals(existingOrder.getOrderID(), orders.get(0).getOrderID());
            assertTrue(nine.getAttribute("class").contains("occupied"));
            System.out.printf("重复预约已拦截：原订单orderID=%d，日期=%s，09:00—11:00%n",
                    existingOrder.getOrderID(), day);
        } finally {
            driver.quit();
        }
    }

    private Order saveOrder(User user, Venue venue, LocalDateTime startTime, int hours) {
        Order order = new Order();
        order.setUserID(user.getUserID());
        order.setVenueID(venue.getVenueID());
        order.setOrderTime(LocalDateTime.now());
        order.setStartTime(startTime);
        order.setHours(hours);
        order.setTotal(hours * venue.getPrice());
        order.setState(1);
        return orderDao.saveAndFlush(order);
    }

    private List<Order> matchingOrders(User user, Venue venue, LocalDateTime startTime) {
        return orderDao.findByVenueIDAndStartTimeIsBetween(
                        venue.getVenueID(), startTime, startTime).stream()
                .filter(order -> user.getUserID().equals(order.getUserID()))
                .collect(Collectors.toList());
    }

    private LocalDate findAvailableDay(List<Order> orders, Venue venue, int startHour, int endHour) {
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate day = LocalDate.now().plusDays(offset);
            LocalDateTime start = day.atTime(startHour, 0);
            LocalDateTime end = day.atTime(endHour, 0);
            boolean conflict = orders.stream().anyMatch(order -> order.getStartTime() != null
                    && order.getVenueID() == venue.getVenueID()
                    && order.getStartTime().isBefore(end)
                    && order.getStartTime().plusHours(order.getHours()).isAfter(start));
            if (!conflict) return day;
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
