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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=none")
class Reservation021Test {
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-021 部分时段发生重叠")
    void shouldRejectPartiallyOverlappingReservation() throws Exception {
        User user = userDao.findByUserID("user01");
        Venue venue = venueDao.findByVenueName("篮球馆");
        assertNotNull(user, "请先运行第一条用例创建user01");
        assertNotNull(venue, "请先添加篮球馆");
        LocalDate day = ReservationTestSupport.findDay(orderDao.findAll(),
                order -> order.getVenueID() == venue.getVenueID(), 10, 13);
        assertNotNull(day, "未来30天内没有可用日期");
        Order original = ReservationTestSupport.saveOrder(
                orderDao, user, venue, day.atTime(10, 0), 2);
        Set<Integer> oldIds = ReservationTestSupport.ids(orderDao.findAll());

        WebDriver driver = ChromeDriverSupport.openBrowser();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            ReservationTestSupport.login(driver, wait, user);
            driver.get("http://localhost:8888/order_place.do?venueID=" + venue.getVenueID());
            ReservationTestSupport.selectDate(driver, wait, day);
            wait.until(webDriver -> driver.findElement(By.id("11"))
                    .getAttribute("class").contains("occupied"));
            Thread.sleep(2500);
            ReservationTestSupport.drag(driver, "11", 100);
            driver.findElement(By.id("submit")).click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            String message = alert.getText();
            alert.accept();
            Thread.sleep(2500);

            Set<Integer> newIds = ReservationTestSupport.newIds(orderDao, oldIds);
            if (!newIds.isEmpty()) {
                fail("拖选11:00—13:00时系统生成了其他时段订单，页面提示：" + message
                        + "；异常订单编号：" + newIds);
            }
            System.out.printf("重叠预约已拦截：原订单orderID=%d，日期=%s，10:00—12:00%n",
                    original.getOrderID(), day);
        } finally {
            driver.quit();
        }
    }
}
