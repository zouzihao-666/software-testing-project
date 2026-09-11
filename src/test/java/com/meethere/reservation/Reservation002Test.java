package com.meethere.reservation;

import com.meethere.dao.*;
import com.meethere.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
class Reservation002Test {
    @Autowired private MockMvc mvc;
    @Autowired private UserDao userDao;
    @Autowired private VenueDao venueDao;
    @Autowired private OrderDao orderDao;

    @Test
    @DisplayName("RES-H-002 真实数据库：羽毛球馆14:00—16:00，未审核，200元")
    void shouldSubmitTwoHourBadmintonReservation() throws Exception {
        // 自动准备真实账号，已有账号不重复注册或修改密码。
        User user = prepareUser();
        assertEquals(0, user.getIsadmin(), "user01必须是普通用户");
        Venue venue = venueDao.findByVenueName("羽毛球馆");
        assertNotNull(venue, "请先添加羽毛球馆");
        assertEquals(100, venue.getPrice(), "羽毛球馆须为100元/小时");
        assertFalse(LocalTime.parse(venue.getOpen_time()).isAfter(LocalTime.of(14, 0)), "14:00须已开馆");
        assertFalse(LocalTime.parse(venue.getClose_time()).isBefore(LocalTime.of(16, 0)), "16:00须未闭馆");

        // 从现有订单中选择未来30天内没有预约的一天，重复运行不重复占用同一时段。
        List<Order> existing = orderDao.findAll();
        LocalDate day = null;
        for (int offset = 1; offset <= 30; offset++) {
            LocalDate candidate = LocalDate.now().plusDays(offset);
            boolean occupied = existing.stream().anyMatch(o ->
                    o.getVenueID() == venue.getVenueID()
                    && o.getStartTime() != null
                    && o.getStartTime().isBefore(candidate.plusDays(1).atStartOfDay())
                    && o.getStartTime().plusHours(o.getHours()).isAfter(candidate.atStartOfDay()));
            LocalDateTime candidateStart = candidate.atTime(14, 0);
            boolean userOccupied = existing.stream().anyMatch(o ->
                    user.getUserID().equals(o.getUserID()) && o.getStartTime() != null
                    && o.getStartTime().isBefore(candidateStart.plusHours(2))
                    && o.getStartTime().plusHours(o.getHours()).isAfter(candidateStart));
            if (!occupied && !userOccupied) { day = candidate; break; }
        }
        assertNotNull(day, "未来30天内没有空闲测试日期");
        LocalDateTime start = day.atTime(14, 0);
        assertFalse(existing.stream().anyMatch(o -> user.getUserID().equals(o.getUserID())
                && o.getStartTime() != null && o.getStartTime().isBefore(start.plusHours(2))
                && o.getStartTime().plusHours(o.getHours()).isAfter(start)), "用户在所选时段已有其他预约");

        // 使用真实登录逻辑，现有密码不打印到日志。
        MvcResult login = mvc.perform(post("/loginCheck.do")
                        .param("userID", user.getUserID()).param("password", user.getPassword()))
                .andExpect(status().isOk()).andExpect(content().string("/index")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "登录应建立会话");

        // 不使用事务回滚：订单提交到真实数据库后保留。
        mvc.perform(post("/addOrder.do").session(session)
                        .param("venueName", venue.getVenueName()).param("date", day.toString())
                        .param("startTime", start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                        .param("hours", "2"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("order_manage"));

        List<Order> saved = orderDao.findByVenueIDAndStartTimeIsBetween(venue.getVenueID(), start, start)
                .stream().filter(o -> user.getUserID().equals(o.getUserID())).collect(Collectors.toList());
        assertEquals(1, saved.size(), "数据库中应有一笔对应的新订单");
        Order order = saved.get(0);
        System.out.printf("真实订单已保留：orderID=%d，用户=%s，日期=%s，14:00—16:00%n",
                order.getOrderID(), user.getUserID(), day);
        assertAll("核对数据库订单",
                () -> assertTrue(order.getOrderID() > 0),
                () -> assertEquals(user.getUserID(), order.getUserID()),
                () -> assertEquals(venue.getVenueID(), order.getVenueID()),
                () -> assertEquals(start, order.getStartTime()),
                () -> assertEquals(2, order.getHours()),
                () -> assertEquals(start.plusHours(2), order.getStartTime().plusHours(order.getHours()), "结束时间16:00"),
                () -> assertEquals(1, order.getState(), "未审核"),
                () -> assertEquals(200, order.getTotal()),
                () -> assertNotNull(order.getOrderTime()));
    }

    private User prepareUser() throws Exception {
        User existing = userDao.findByUserID("user01");
        if (existing != null) {
            return existing;
        }
        mvc.perform(post("/register.do")
                        .param("userID", "user01")
                        .param("userName", "预约测试用户")
                        .param("password", "TestUser01!")
                        .param("email", "user01@example.com")
                        .param("phone", ""))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("login"));
        User created = userDao.findByUserID("user01");
        assertNotNull(created, "注册接口执行后，真实数据库中应存在user01");
        assertEquals(0, created.getIsadmin(), "自动注册的用户应为普通用户");
        System.out.println("已通过注册接口创建真实用户user01");
        return created;
    }
}
