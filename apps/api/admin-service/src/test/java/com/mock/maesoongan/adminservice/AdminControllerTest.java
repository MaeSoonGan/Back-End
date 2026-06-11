package com.mock.maesoongan.adminservice;

import com.mock.maesoongan.adminservice.auth.AdminAuthController;
import com.mock.maesoongan.adminservice.auth.AdminAuthDtos;
import com.mock.maesoongan.adminservice.auth.AdminAuthService;
import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.adminservice.contest.AdminContestController;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos;
import com.mock.maesoongan.adminservice.contest.AdminContestService;
import com.mock.maesoongan.adminservice.dashboard.AdminDashboardController;
import com.mock.maesoongan.adminservice.dashboard.AdminDashboardDtos;
import com.mock.maesoongan.adminservice.dashboard.AdminDashboardService;
import com.mock.maesoongan.adminservice.member.AdminMemberController;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos;
import com.mock.maesoongan.adminservice.member.AdminMemberService;
import com.mock.maesoongan.adminservice.notice.AdminNoticeController;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos;
import com.mock.maesoongan.adminservice.notice.AdminNoticeService;
import com.mock.maesoongan.adminservice.system.AdminSystemController;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos;
import com.mock.maesoongan.adminservice.system.AdminSystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminControllerTest {

    private AdminAuthService authService;
    private AdminDashboardService dashboardService;
    private AdminContestService contestService;
    private AdminMemberService memberService;
    private AdminNoticeService noticeService;
    private AdminSystemService systemService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AdminAuthService.class);
        dashboardService = mock(AdminDashboardService.class);
        contestService = mock(AdminContestService.class);
        memberService = mock(AdminMemberService.class);
        noticeService = mock(AdminNoticeService.class);
        systemService = mock(AdminSystemService.class);

        mockMvc = standaloneSetup(
                        new AdminAuthController(authService),
                        new AdminDashboardController(dashboardService),
                        new AdminContestController(contestService),
                        new AdminMemberController(memberService),
                        new AdminNoticeController(noticeService),
                        new AdminSystemController(systemService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsAdminToken() throws Exception {
        when(authService.login(any())).thenReturn(new AdminAuthDtos.AdminLoginResponse(
                "admin-token",
                "admin",
                "Admin",
                "SUPER_ADMIN"
        ));

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "admin",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", is("admin-token")))
                .andExpect(jsonPath("$.data.role", is("SUPER_ADMIN")));
    }

    @Test
    void loginReturnsUnauthorizedWhenAuthenticationFails() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_FAILED", "Login failed"));

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "admin",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("ADMIN_LOGIN_FAILED")));
    }

    @Test
    void getDashboardReturnsDashboardSummary() throws Exception {
        when(dashboardService.getDashboard()).thenReturn(dashboardResponse());

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers", is(100)))
                .andExpect(jsonPath("$.data.alerts[0].alertId", is(1)))
                .andExpect(jsonPath("$.data.dailyOrders[0].orderCount", is(30)));
    }

    @Test
    void getContestSummaryReturnsSummaryCards() throws Exception {
        when(contestService.getContestSummary()).thenReturn(new AdminContestDtos.ContestSummaryResponse(
                10,
                2,
                3,
                4,
                1,
                120
        ));

        mockMvc.perform(get("/api/admin/contests/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalContestCount", is(10)))
                .andExpect(jsonPath("$.data.activeContestCount", is(3)));
    }

    @Test
    void getContestsReturnsContestPage() throws Exception {
        when(contestService.getContests("mock", "ACTIVE", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 0, 10))
                .thenReturn(new AdminContestDtos.PageResponse<>(
                        List.of(contestListItem()),
                        1,
                        1,
                        0
                ));

        mockMvc.perform(get("/api/admin/contests")
                        .param("keyword", "mock")
                        .param("status", "ACTIVE")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contestId", is(3)))
                .andExpect(jsonPath("$.data.totalElements", is(1)));
    }

    @Test
    void createContestReturnsCreatedContestStatus() throws Exception {
        when(contestService.createContest(any())).thenReturn(new AdminContestDtos.ContestMutationResponse(
                3,
                "SCHEDULED",
                "Contest created"
        ));

        mockMvc.perform(post("/api/admin/contests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mock Contest",
                                  "description": "description",
                                  "seedMoney": 10000000,
                                  "startAt": "2026-06-20T09:00:00",
                                  "endAt": "2026-06-30T15:30:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestId", is(3)))
                .andExpect(jsonPath("$.data.status", is("SCHEDULED")));
    }

    @Test
    void createContestReturnsBadRequestWhenTitleIsMissing() throws Exception {
        mockMvc.perform(post("/api/admin/contests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seedMoney": 10000000,
                                  "startAt": "2026-06-20T09:00:00",
                                  "endAt": "2026-06-30T15:30:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void exportContestRankingsReturnsCsv() throws Exception {
        when(contestService.exportRankings(3L, "win", "ALL"))
                .thenReturn("contestId,memberId,nickname\n3,7,winner\n");

        mockMvc.perform(get("/api/admin/contests/{contestId}/rankings/export", 3L)
                        .param("keyword", "win"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("contest-3-rankings.csv")))
                .andExpect(content().string(containsString("winner")));
    }

    @Test
    void getMemberSummaryReturnsSummaryCards() throws Exception {
        when(memberService.getMemberSummary()).thenReturn(new AdminMemberDtos.MemberSummaryResponse(
                100,
                90,
                5,
                3
        ));

        mockMvc.perform(get("/api/admin/members/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount", is(100)))
                .andExpect(jsonPath("$.data.suspendedCount", is(5)));
    }

    @Test
    void searchMembersReturnsMembers() throws Exception {
        when(memberService.searchMembers("user", 10)).thenReturn(List.of(new AdminMemberDtos.MemberSearchItem(
                7,
                "user",
                "user01",
                "use***@example.com",
                "ACTIVE"
        )));

        mockMvc.perform(get("/api/admin/members/search").param("keyword", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].memberId", is(7)))
                .andExpect(jsonPath("$.data[0].status", is("ACTIVE")));
    }

    @Test
    void suspendMembersReturnsSuspensionResult() throws Exception {
        when(memberService.suspendMembers(any())).thenReturn(new AdminMemberDtos.SuspendMembersResponse(
                2,
                1,
                1,
                0,
                List.of(7L),
                List.of(8L),
                List.of(),
                "Suspended"
        ));

        mockMvc.perform(patch("/api/admin/members/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberIds": [7, 8],
                                  "reason": "abnormal activity"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspendedCount", is(1)))
                .andExpect(jsonPath("$.data.skippedCount", is(1)));
    }

    @Test
    void suspendMembersReturnsBadRequestWhenReasonIsMissing() throws Exception {
        mockMvc.perform(patch("/api/admin/members/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberIds": [7]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void exportMembersReturnsCsv() throws Exception {
        when(memberService.exportMembers(any(), eq("ALL"), any(), any()))
                .thenReturn("memberId,nickname\n7,user\n");

        mockMvc.perform(get("/api/admin/members/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("members.csv")))
                .andExpect(content().string(containsString("user")));
    }

    @Test
    void getNoticesReturnsNoticePage() throws Exception {
        when(noticeService.getNotices("event", "PUBLISHED", 0, 10, null))
                .thenReturn(new AdminNoticeDtos.PageResponse<>(
                        List.of(noticeListItem()),
                        1,
                        1,
                        0
                ));

        mockMvc.perform(get("/api/admin/notices")
                        .param("keyword", "event")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].noticeId", is(5)))
                .andExpect(jsonPath("$.data.content[0].isPinned", is(true)));
    }

    @Test
    void createNoticeReturnsMutationResult() throws Exception {
        when(noticeService.createNotice(any())).thenReturn(new AdminNoticeDtos.NoticeMutationResponse(
                5,
                "PUBLISHED",
                "Notice created"
        ));

        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Notice",
                                  "content": "content",
                                  "isPinned": true,
                                  "status": "PUBLISHED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeId", is(5)))
                .andExpect(jsonPath("$.data.status", is("PUBLISHED")));
    }

    @Test
    void createNoticeReturnsBadRequestWhenContentIsMissing() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Notice"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void getNoticeReturnsNotFoundWhenNoticeDoesNotExist() throws Exception {
        when(noticeService.getNotice(999L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notice not found"));

        mockMvc.perform(get("/api/admin/notices/{noticeId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    void getMonitoringReturnsMonitoringData() throws Exception {
        when(systemService.getMonitoring()).thenReturn(monitoringResponse());

        mockMvc.perform(get("/api/admin/monitoring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayOrders", is(20)))
                .andExpect(jsonPath("$.data.alerts[0].alertId", is(9)));
    }

    @Test
    void updateMaintenanceReturnsMaintenanceStatus() throws Exception {
        when(systemService.updateMaintenance(any())).thenReturn(new AdminSystemDtos.MaintenanceResponse(
                "ON",
                true,
                "maintenance",
                LocalDateTime.of(2026, 6, 11, 10, 0)
        ));

        mockMvc.perform(patch("/api/admin/system/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ON",
                                  "message": "maintenance"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ON")))
                .andExpect(jsonPath("$.data.enabled", is(true)));
    }

    @Test
    void getAuditLogsReturnsAuditPage() throws Exception {
        when(systemService.getAuditLogs(null, null, null, "ALL", null, 0, 8))
                .thenReturn(new AdminSystemDtos.PageResponse<>(
                        List.of(new AdminSystemDtos.AuditLogItem(
                                1,
                                "MEMBER",
                                "SUSPEND_MEMBER",
                                "MEMBER",
                                7L,
                                "reason",
                                1L,
                                "admin",
                                "127.0.0.1",
                                LocalDateTime.of(2026, 6, 11, 10, 0)
                        )),
                        1,
                        1,
                        0
                ));

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].logId", is(1)))
                .andExpect(jsonPath("$.data.content[0].type", is("MEMBER")));
    }

    @Test
    void forceCancelOrderReturnsCanceledStatus() throws Exception {
        when(systemService.forceCancelOrder(eq(5001L), any())).thenReturn(new AdminSystemDtos.ForceCancelOrderResponse(
                5001,
                "PENDING",
                "CANCELED",
                "Order canceled"
        ));

        mockMvc.perform(patch("/api/admin/orders/{orderId}/cancel", 5001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "abnormal order"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId", is(5001)))
                .andExpect(jsonPath("$.data.status", is("CANCELED")));
    }

    @Test
    void getAdminsReturnsAdminList() throws Exception {
        when(systemService.getAdmins()).thenReturn(List.of(new AdminSystemDtos.AdminListItem(
                1,
                "admin",
                "Admin",
                "admin@example.com",
                "SUPER_ADMIN",
                "ACTIVE"
        )));

        mockMvc.perform(get("/api/admin/admins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].adminId", is(1)))
                .andExpect(jsonPath("$.data[0].role", is("SUPER_ADMIN")));
    }

    private AdminDashboardDtos.DashboardResponse dashboardResponse() {
        return new AdminDashboardDtos.DashboardResponse(
                100,
                3,
                50,
                45,
                2,
                30,
                1,
                List.of(new AdminDashboardDtos.AlertSummary(
                        1,
                        "ABNORMAL_ORDER",
                        7,
                        "user",
                        5001L,
                        "alert",
                        LocalDateTime.of(2026, 6, 11, 10, 0)
                )),
                List.of(new AdminDashboardDtos.DailyOrder(LocalDate.of(2026, 6, 11), 30, true)),
                List.of(new AdminDashboardDtos.ActiveContestSummary(3, "Mock Contest", "06.01-06.30", 30, "ACTIVE")),
                List.of(new AdminDashboardDtos.ActivitySummary(1, "NOTICE", "created", 1L, "admin", LocalDateTime.of(2026, 6, 11, 10, 0))),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }

    private AdminContestDtos.ContestListItem contestListItem() {
        return new AdminContestDtos.ContestListItem(
                3,
                "Mock Contest",
                "2026.06.20-2026.06.30",
                new BigDecimal("10000000"),
                100,
                12,
                "ACTIVE",
                true,
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 30, 15, 30)
        );
    }

    private AdminNoticeDtos.NoticeListItem noticeListItem() {
        return new AdminNoticeDtos.NoticeListItem(
                5,
                "Notice",
                true,
                "PUBLISHED",
                LocalDateTime.of(2026, 6, 11, 9, 0),
                null,
                1L,
                "admin",
                LocalDateTime.of(2026, 6, 11, 9, 0),
                null
        );
    }

    private AdminSystemDtos.MonitoringResponse monitoringResponse() {
        return new AdminSystemDtos.MonitoringResponse(
                20,
                18,
                30,
                1,
                new AdminSystemDtos.MaintenanceResponse("OFF", false, "normal", null),
                List.of(new AdminSystemDtos.MonitoringAlertItem(
                        9,
                        "ABNORMAL_ORDER",
                        "ORDER",
                        5001L,
                        7L,
                        "user",
                        5001L,
                        "alert",
                        "message",
                        "PENDING",
                        LocalDateTime.of(2026, 6, 11, 10, 0)
                )),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }
}
