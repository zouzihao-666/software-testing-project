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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation024Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-024 同一用户预约两个冲突场馆")
    void shouldRejectUserTimeConflictAcrossVenues() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue basketball = venueDao.findByVenueName("篮球馆");
        Venue badminton = venueDao.findByVenueName("羽毛球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(basketball, "请先添加篮球馆");
        assertNotNull(badminton, "请先添加羽毛球馆");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == basketball.getVenueID()
                        || order.getVenueID() == badminton.getVenueID(), 10, 13);
        assertNotNull(day, "未来30天内没有两个场馆同时空闲的日期");
        Order original = ReservationTestSupport.saveOrder(
                orderDao, user, basketball, day.atTime(10, 0), 2);
        Set<Integer> oldIds = ReservationTestSupport.ids(orderDao.findAll());

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + badminton.getVenueID());
            ReservationTestSupport.selectDate(driver, wait, day);
            wait.until(webDriver -> !driver.findElement(By.id("11"))
                    .getAttribute("class").contains("occupied"));
            ReservationTestSupport.drag(driver, "11", 55);
            Thread.sleep(2500);
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            String message = alert.getText();
            alert.accept();
            Thread.sleep(2500);

            Set<Integer> newIds = ReservationTestSupport.newIds(orderDao, oldIds);
            if (!newIds.isEmpty()) {
                List<Order> newOrders = orderDao.findAllById(newIds);
                boolean userConflict = newOrders.stream().anyMatch(order ->
                        user.getUserID().equals(order.getUserID())
                                && order.getVenueID() == badminton.getVenueID());
                if (userConflict) {
                    fail("系统允许同一用户预约两个时间冲突的场馆，页面提示：" + message
                            + "；原订单：" + original.getOrderID() + "，异常订单：" + newIds);
                }
                fail("提交后生成了异常订单：" + newIds);
            }
        } finally {
            driver.quit();
        }
    }
}
