package com.mock.maesoongan.admin.dashboard;

import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ActivityListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.AlertListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.CancelOrderResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestStatisticsResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DailyOrderListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DashboardResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.IgnoreAlertResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ReasonRequest;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.SuspendUserResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.TodayOrderStatisticsResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.UserStatisticsResponse;
import com.mock.maesoongan.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @Operation(summary = "대시보드 전체 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대시보드 전체 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 실패", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content)
    })
    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> getDashboard() {
        return ApiResponse.success(adminDashboardService.getDashboard());
    }

    @Operation(summary = "전체 회원 통계 조회")
    @GetMapping("/dashboard/users/statistics")
    public ApiResponse<UserStatisticsResponse> getUserStatistics() {
        return ApiResponse.success(adminDashboardService.getUserStatistics());
    }

    @Operation(summary = "오늘 주문 통계 조회")
    @GetMapping("/dashboard/orders/today")
    public ApiResponse<TodayOrderStatisticsResponse> getTodayOrderStatistics() {
        return ApiResponse.success(adminDashboardService.getTodayOrderStatistics());
    }

    @Operation(summary = "진행 중 대회 통계 조회")
    @GetMapping("/dashboard/contests/statistics")
    public ApiResponse<ContestStatisticsResponse> getContestStatistics() {
        return ApiResponse.success(adminDashboardService.getContestStatistics());
    }

    @Operation(summary = "비정상 탐지 알림 조회")
    @GetMapping("/dashboard/alerts")
    public ApiResponse<AlertListResponse> getAlerts(
            @Parameter(description = "알림 상태", example = "PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "조회 개수", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.success(adminDashboardService.getAlerts(status, limit));
    }

    @Operation(summary = "회원 계정 정지")
    @PatchMapping("/users/{userId}/suspend")
    public ApiResponse<SuspendUserResponse> suspendUser(
            @PathVariable long userId,
            @Valid @RequestBody(required = false) ReasonRequest request
    ) {
        return ApiResponse.success(adminDashboardService.suspendUser(userId, reasonOf(request)));
    }

    @Operation(summary = "주문 강제 취소")
    @PatchMapping("/orders/{orderId}/cancel")
    public ApiResponse<CancelOrderResponse> cancelOrder(
            @PathVariable long orderId,
            @Valid @RequestBody(required = false) ReasonRequest request
    ) {
        return ApiResponse.success(adminDashboardService.cancelOrder(orderId, reasonOf(request)));
    }

    @Operation(summary = "알림 무시 처리")
    @PatchMapping("/dashboard/alerts/{alertId}/ignore")
    public ApiResponse<IgnoreAlertResponse> ignoreAlert(
            @PathVariable long alertId,
            @Valid @RequestBody(required = false) ReasonRequest request
    ) {
        return ApiResponse.success(adminDashboardService.ignoreAlert(alertId, reasonOf(request)));
    }

    @Operation(summary = "최근 7일 일별 주문 건수 조회")
    @GetMapping("/dashboard/orders/daily")
    public ApiResponse<DailyOrderListResponse> getDailyOrders(
            @Parameter(description = "조회할 일수", example = "7")
            @RequestParam(defaultValue = "7") @Min(1) @Max(31) int days
    ) {
        return ApiResponse.success(adminDashboardService.getDailyOrders(days));
    }

    @Operation(summary = "진행 중 대회 목록 조회")
    @GetMapping("/dashboard/contests")
    public ApiResponse<ContestListResponse> getContests(
            @Parameter(description = "대회 상태", example = "ACTIVE")
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(adminDashboardService.getContests(status));
    }

    @Operation(summary = "최근 관리 활동 조회")
    @GetMapping("/dashboard/activities")
    public ApiResponse<ActivityListResponse> getActivities(
            @Parameter(description = "조회 개수", example = "5")
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.success(adminDashboardService.getActivities(limit));
    }
    private String reasonOf(ReasonRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return null;
        }
        return request.reason();
    }
}
