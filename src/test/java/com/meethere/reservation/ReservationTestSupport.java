package com.meethere.reservation;

import com.meethere.dao.OrderDao;
import com.meethere.entity.Order;
import com.meethere.entity.User;
import com.meethere.entity.Venue;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReservationTestSupport {
    private ReservationTestSupport() { }

    static void login(WebDriver driver, WebDriverWait wait, User user) {
        driver.get("http://localhost:8888/login");
        driver.findElement(By.id("userID")).sendKeys(user.getUserID());
        driver.findElement(By.id("password")).sendKeys(user.getPassword());
        driver.findElement(By.id("submit")).click();
        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        assertEquals("登录成功！", alert.getText());
        alert.accept();
        wait.until(ExpectedConditions.urlMatches(".*/index$"));
    }

    static void selectDate(WebDriver driver, WebDriverWait wait, LocalDate day) {
        WebElement date = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("date")));
        date.sendKeys(Keys.CONTROL, "a");
        date.sendKeys(day.toString());
        date.sendKeys(Keys.TAB);
    }

    static void drag(WebDriver driver, String startHourId, int width) {
        new Actions(driver).moveToElement(driver.findElement(By.id(startHourId)))
                .clickAndHold().moveByOffset(width, 0).release().perform();
    }

    static Order saveOrder(OrderDao dao, User user, Venue venue,
                           LocalDateTime startTime, int hours) {
        Order order = new Order();
        order.setUserID(user.getUserID());
        order.setVenueID(venue.getVenueID());
        order.setOrderTime(LocalDateTime.now());
        order.setStartTime(startTime);
        order.setHours(hours);
        order.setTotal(hours * venue.getPrice());
        order.setState(1);
        return dao.saveAndFlush(order);
    }

    static LocalDate findDay(List<Order> orders, Predicate<Order> relevant,
                             int startHour, int endHour) {
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate day = LocalDate.now().plusDays(offset);
            LocalDateTime start = day.atTime(startHour, 0);
            LocalDateTime end = day.atTime(endHour, 0);
            boolean conflict = orders.stream().filter(relevant)
                    .anyMatch(order -> order.getStartTime() != null
                            && order.getStartTime().isBefore(end)
                            && order.getStartTime().plusHours(order.getHours()).isAfter(start));
            if (!conflict) return day;
        }
        return null;
    }

    static Set<Integer> ids(List<Order> orders) {
        return orders.stream().map(Order::getOrderID).collect(Collectors.toSet());
    }

    static Set<Integer> newIds(OrderDao dao, Set<Integer> oldIds) {
        Set<Integer> result = new HashSet<>(ids(dao.findAll()));
        result.removeAll(oldIds);
        return result;
    }
}
