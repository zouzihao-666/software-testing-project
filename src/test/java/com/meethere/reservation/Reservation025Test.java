package com.meethere.reservation;

import com.meethere.dao.OrderDao;
import com.meethere.dao.UserDao;
import com.meethere.dao.VenueDao;
import com.meethere.entity.Order;
import com.meethere.entity.User;
import com.meethere.entity.Venue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation025Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-025 查看个人预约记录")
    void shouldShowPersonalReservationCorrectly() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID(), 14, 16);
        assertNotNull(day, "未来30天内没有可用日期");
        Order order = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(14, 0), 2);

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_manage");
            WebElement content = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("content")));
            wait.until(webDriver -> content.getText().contains(day.format(DateTimeFormatter.ISO_DATE)));
            Thread.sleep(3000);

            String text = content.getText();
            assertTrue(text.contains("篮球馆"), "应显示场馆名称");
            assertTrue(text.contains(day.toString()) && text.contains("14:00"), "应显示预约时间");
            assertTrue(text.contains("预约时长：") && text.contains("2"), "应显示2小时");
            assertTrue(text.contains("支付租金：") && text.contains("400"), "应显示400元");
            assertTrue(text.contains("审核中"), "应显示审核中状态");
            System.out.printf("个人预约显示正确：orderID=%d，日期=%s，14:00—16:00%n",
                    order.getOrderID(), day);
        } finally {
            driver.quit();
        }
    }
}
