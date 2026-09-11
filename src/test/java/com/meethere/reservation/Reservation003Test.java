package com.meethere.reservation;

import com.meethere.dao.OrderDao;
import com.meethere.dao.VenueDao;
import com.meethere.entity.Venue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation003Test {

    @Autowired
    private VenueDao venueDao;

    @Autowired
    private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-003 未登录用户预约")
    void shouldAskUserToLoginAndNotCreateOrder() throws Exception {
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(venue, "请先添加篮球馆");
        long orderCountBefore = orderDao.count();

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            driver.get("http://localhost:8888/venue?venueID=" + venue.getVenueID());

            WebElement reserveButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("a[onclick='order_venue()']")));
            assertEquals("", driver.findElement(By.id("user")).getAttribute("innerHTML"),
                    "当前页面仍处于登录状态");
            Thread.sleep(2000);
            reserveButton.click();

            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("请登录！", alert.getText());
            Thread.sleep(3000);
            alert.accept();

            wait.until(ExpectedConditions.urlMatches(".*/login$"));
            assertTrue(driver.getCurrentUrl().endsWith("/login"));
            assertEquals(orderCountBefore, orderDao.count(), "未登录操作不能生成订单");
            Thread.sleep(3000);
        } finally {
            driver.quit();
        }
    }
}
