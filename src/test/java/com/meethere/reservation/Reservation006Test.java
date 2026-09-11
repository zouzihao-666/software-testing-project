package com.meethere.reservation;

import com.meethere.dao.OrderDao;
import com.meethere.dao.UserDao;
import com.meethere.dao.VenueDao;
import com.meethere.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation006Test {

    private static final String VENUE_NAME_WITH_SPACES = " 篮球馆 ";

    @Autowired
    private UserDao userDao;

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-006 场馆名称前后有空格")
    void shouldHandleVenueNameWithSurroundingSpaces() throws Exception {
        User user = userDao.findByUserID("user01");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertEquals(0, user.getIsadmin(), "user01必须是普通用户");
        assertNotNull(venueDao.findByVenueName("篮球馆"), "请先添加篮球馆");

        LocalDate day = LocalDate.now().plusDays(1);
        long orderCountBefore = orderDao.count();
        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            login(driver, wait, user);
            driver.get("http://localhost:8888/order_place");

            WebElement venueName = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("venueName")));
            venueName.sendKeys(VENUE_NAME_WITH_SPACES);
            venueName.sendKeys(Keys.TAB);
            assertEquals(VENUE_NAME_WITH_SPACES, venueName.getAttribute("value"),
                    "输入内容应包含前后空格");

            WebElement venueAlert = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("alertVenue")));
            assertEquals("场馆不存在！", venueAlert.getText().trim(),
                    "系统应给出明确的场馆校验提示");

            WebElement dateInput = driver.findElement(By.id("date"));
            dateInput.sendKeys(Keys.CONTROL, "a");
            dateInput.sendKeys(day.toString());
            dateInput.sendKeys(Keys.TAB);

            WebElement nineOClock = wait.until(ExpectedConditions.elementToBeClickable(By.id("9")));
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

            WebElement submitButton = driver.findElement(By.id("submit"));
            assertFalse(submitButton.isEnabled(), "校验未通过时提交按钮应不可用");
            Thread.sleep(2000);
            submitButton.click();

            assertTrue(venueAlert.isDisplayed(), "场馆校验提示应保持显示");
            assertTrue(driver.getCurrentUrl().endsWith("/order_place"), "页面不应跳转到异常页");
            assertFalse(driver.getPageSource().contains("Whitelabel Error Page"),
                    "页面不能出现系统异常");
            assertEquals(orderCountBefore, orderDao.count(), "不能错误生成订单");
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
}
