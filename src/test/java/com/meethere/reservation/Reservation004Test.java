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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation004Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-004 场馆名称为空")
    void shouldRejectReservationWhenVenueNameIsEmpty() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertEquals(0, user.getIsadmin(), "user01必须是普通用户");

        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = findAvailableDay(venue.getVenueID());
        assertNotNull(day, "未来30天内没有可测试的09:00—10:00时段");
        long orderCountBefore = orderDao.count();

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            login(driver, wait, user);

            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());
            WebElement dateInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("date")));
            dateInput.sendKeys(Keys.CONTROL, "a");
            dateInput.sendKeys(day.toString());
            dateInput.sendKeys(Keys.TAB);

            WebElement nineOClock = wait.until(ExpectedConditions.elementToBeClickable(By.id("9")));
            wait.until(webDriver -> !nineOClock.getAttribute("class").contains("occupied")
                    && !nineOClock.getAttribute("class").contains("banned"));
            new Actions(driver)
                    .moveToElement(nineOClock)
                    .clickAndHold()
                    .moveByOffset(1, 0)
                    .release()
                    .perform();

            assertTrue(driver.findElement(By.id("startTime")).getAttribute("value")
                    .endsWith("09:00"), "开始时间应为09:00");
            assertEquals("1", driver.findElement(By.id("hours")).getAttribute("value"),
                    "预约时长应为1小时");

            WebElement venueName = driver.findElement(By.id("venueName"));
            venueName.clear();
            venueName.sendKeys(Keys.TAB);
            assertEquals("", venueName.getAttribute("value"));
            Thread.sleep(2000);

            driver.findElement(By.id("submit")).click();
            String validationMessage = venueName.getAttribute("validationMessage");
            assertFalse(validationMessage.isEmpty(), "应提示场馆名称不能为空");
            assertTrue(driver.getCurrentUrl().contains("/order_place.do"), "提交后应留在预约页面");
            assertEquals(orderCountBefore, orderDao.count(), "场馆名称为空时不能生成订单");
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
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

    private LocalDate findAvailableDay(int venueID) {
        List<Order> orders = orderDao.findAll();
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate candidate = LocalDate.now().plusDays(offset);
            LocalDateTime start = candidate.atTime(9, 0);
            LocalDateTime end = start.plusHours(1);
            boolean occupied = orders.stream().anyMatch(order ->
                    order.getVenueID() == venueID
                            && order.getStartTime() != null
                            && order.getStartTime().isBefore(end)
                            && order.getStartTime().plusHours(order.getHours()).isAfter(start));
            if (!occupied) {
                return candidate;
            }
        }
        return null;
    }
}
