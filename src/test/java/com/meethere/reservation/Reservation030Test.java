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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation030Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-030 确认删除预约")
    void shouldDeleteOrderAfterConfirmation() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID(), 19, 20);
        assertNotNull(day, "未来30天内没有可用日期");
        Order order = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(19, 0), 1);
        int orderId = order.getOrderID();

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_manage");
            WebElement item = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                    "//li[.//a[contains(@onclick,'del(" + orderId + ",this)')]]")));
            WebElement delete = item.findElement(By.xpath(
                    ".//a[contains(@onclick,'del(" + orderId + ",this)')]"));
            Thread.sleep(2500);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", delete);
            Alert confirm = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("确定删除该订单？", confirm.getText());
            confirm.accept();
            Alert success = wait.until(ExpectedConditions.alertIsPresent());
            assertEquals("删除成功！", success.getText());
            success.accept();
            wait.until(ExpectedConditions.invisibilityOf(item));
            Thread.sleep(2500);

            assertNull(orderDao.findByOrderID(orderId), "确认删除后数据库中不应存在该订单");
            System.out.printf("订单删除成功：orderID=%d，列表中已移除%n", orderId);
        } finally {
            driver.quit();
        }
    }
}
