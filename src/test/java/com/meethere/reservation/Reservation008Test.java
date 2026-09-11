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
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation008Test {

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-008 预约当天已过去时间")
    void shouldRejectElapsedTimeToday() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertEquals(0, user.getIsadmin(), "user01必须是普通用户");

        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate today = LocalDate.now();
        long orderCountBefore = orderDao.count();

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            setBrowserTimeToFourPm(driver, today);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());

            WebElement dateInput = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("date")));
            dateInput.sendKeys(Keys.CONTROL, "a");
            dateInput.sendKeys(today.toString());
            dateInput.sendKeys(Keys.TAB);

            WebElement nineOClock = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("9")));
            wait.until(webDriver -> nineOClock.getAttribute("class").contains("banned"));
            assertTrue(nineOClock.getAttribute("class").contains("banned"),
                    "当天09:00—10:00应显示为不可选");
            Thread.sleep(2000);

            new Actions(driver)
                    .moveToElement(nineOClock)
                    .clickAndHold()
                    .moveByOffset(1, 0)
                    .release()
                    .perform();

            assertEquals("", driver.findElement(By.id("startTime")).getAttribute("value"),
                    "已过去时段不能被选中");
            assertEquals("", driver.findElement(By.id("hours")).getAttribute("value"),
                    "已过去时段不能产生预约时长");

            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("您尚未选择预约时间段！", alert.getText());
            Thread.sleep(3000);
            alert.accept();

            assertTrue(driver.getCurrentUrl().contains("/order_place.do"),
                    "提交后应留在预约页面");
            assertEquals(orderCountBefore, orderDao.count(), "不能生成已过期时段的订单");
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }

    private void setBrowserTimeToFourPm(WebDriver driver, LocalDate today) {
        String script = "const NativeDate = Date;"
                + "const fixedTime = new NativeDate('" + today + "T16:00:00').getTime();"
                + "window.Date = class extends NativeDate {"
                + "constructor(...args) {"
                + "if (args.length === 0) { super(fixedTime); } else { super(...args); }"
                + "}"
                + "static now() { return fixedTime; }"
                + "};";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("source", script);
        ((ChromeDriver) driver).executeCdpCommand(
                "Page.addScriptToEvaluateOnNewDocument", parameters);
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
