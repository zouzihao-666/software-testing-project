package com.meethere.reservation;

import com.meethere.dao.OrderDao;
import com.meethere.dao.UserDao;
import com.meethere.dao.VenueDao;
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
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation013Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-013 从闭馆时间开始预约")
    void shouldRejectReservationStartingAtClosingTime() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        assertEquals(LocalTime.of(21, 0), LocalTime.parse(venue.getClose_time()),
                "篮球馆闭馆时间应为21:00");

        LocalDate day = LocalDate.now().plusDays(1);
        long orderCountBefore = orderDao.count();
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

            WebElement closingTime = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("21")));
            wait.until(webDriver -> closingTime.getAttribute("class").contains("banned"));
            assertTrue(closingTime.getAttribute("class").contains("banned"),
                    "21:00—22:00应显示为不可选");
            Thread.sleep(2000);

            new Actions(driver)
                    .moveToElement(closingTime)
                    .clickAndHold()
                    .moveByOffset(1, 0)
                    .release()
                    .perform();

            assertEquals("", driver.findElement(By.id("startTime")).getAttribute("value"));
            assertEquals("", driver.findElement(By.id("hours")).getAttribute("value"));
            driver.findElement(By.id("submit")).click();

            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("您尚未选择预约时间段！", alert.getText());
            Thread.sleep(3000);
            alert.accept();
            assertTrue(driver.getCurrentUrl().contains("/order_place.do"));
            assertEquals(orderCountBefore, orderDao.count(), "不能生成闭馆后的订单");
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
        alert.accept();
        wait.until(ExpectedConditions.urlMatches(".*/index$"));
    }
}
