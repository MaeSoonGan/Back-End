package com.mock.maesoongan.admin.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerTest {

    private static final String ADMIN_TOKEN = "Bearer admin-token";
    private static final String USER_TOKEN = "Bearer user-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getDashboardReturnsDashboardData() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(1234))
                .andExpect(jsonPath("$.data.todayOrders").value(4821))
                .andExpect(jsonPath("$.data.alerts", hasSize(2)))
                .andExpect(jsonPath("$.data.dailyOrders", hasSize(7)))
                .andExpect(jsonPath("$.data.activeContests", hasSize(2)))
                .andExpect(jsonPath("$.data.recentActivities", hasSize(4)));
    }

    @Test
    void adminApiWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminApiWithUserTokenReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", USER_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidAlertStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/alerts")
                        .header("Authorization", ADMIN_TOKEN)
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ALERT_STATUS"));
    }

    @Test
    void suspendUserReturnsSuspendedStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/suspend", 101)
                        .header("Authorization", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"비정상 주문 탐지\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(101))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    void cancelOrderReturnsCanceledStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{orderId}/cancel", 5001)
                        .header("Authorization", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"비정상 주문 탐지\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(5001))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void ignoreAlertReturnsIgnoredStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/dashboard/alerts/{alertId}/ignore", 1)
                        .header("Authorization", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"관리자 확인 결과 이상 없음\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.alertId").value(1))
                .andExpect(jsonPath("$.data.status").value("IGNORED"));
    }
}
