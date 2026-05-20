package com.mock.maesoongan.admin.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDashboardDtos {

    private AdminDashboardDtos() {
    }

    @Schema(description = "대시보드 전체 조회 응답")
    public record DashboardResponse(
            long totalUsers,
            long todayNewUsers,
            long todayOrders,
            long todayCompletedOrders,
            long activeContestCount,
            long activeContestParticipants,
            long abnormalAlertCount,
            List<AlertSummary> alerts,
            List<DailyOrder> dailyOrders,
            List<ActiveContestSummary> activeContests,
            List<ActivitySummary> recentActivities,
            LocalDateTime lastUpdatedAt
    ) {
    }

    @Schema(description = "전체 회원 통계 응답")
    public record UserStatisticsResponse(
            long totalUsers,
            long todayNewUsers
    ) {
    }

    @Schema(description = "오늘 주문 통계 응답")
    public record TodayOrderStatisticsResponse(
            long totalOrderCount,
            long completedOrderCount
    ) {
    }

    @Schema(description = "진행 중 대회 통계 응답")
    public record ContestStatisticsResponse(
            long activeContestCount,
            long totalParticipantCount
    ) {
    }

    @Schema(description = "비정상 탐지 알림 조회 응답")
    public record AlertListResponse(
            long alertCount,
            String systemStatus,
            List<AlertSummary> alerts
    ) {
    }

    @Schema(description = "비정상 탐지 알림")
    public record AlertSummary(
            long alertId,
            String type,
            long userId,
            String userName,
            Long orderId,
            String content,
            LocalDateTime detectedAt
    ) {
    }

    @Schema(description = "최근 일별 주문 건수 응답")
    public record DailyOrderListResponse(
            int days,
            List<DailyOrder> orders
    ) {
    }

    @Schema(description = "일별 주문 건수")
    public record DailyOrder(
            LocalDate date,
            long orderCount,
            boolean isToday
    ) {
    }

    @Schema(description = "진행 중 대회 목록 응답")
    public record ContestListResponse(
            List<ContestDetail> contests
    ) {
    }

    @Schema(description = "대시보드 진행 중 대회 요약")
    public record ActiveContestSummary(
            long contestId,
            String contestName,
            String period,
            long participantCount,
            String status
    ) {
    }

    @Schema(description = "진행 중 대회 상세")
    public record ContestDetail(
            long contestId,
            String contestName,
            LocalDate startDate,
            LocalDate endDate,
            String period,
            long participantCount,
            Long maxParticipantCount,
            String status,
            String statusName
    ) {
    }

    @Schema(description = "최근 관리 활동 조회 응답")
    public record ActivityListResponse(
            List<ActivitySummary> activities
    ) {
    }

    @Schema(description = "최근 관리 활동")
    public record ActivitySummary(
            long activityId,
            String type,
            String content,
            Long adminId,
            String adminName,
            LocalDateTime createdAt
    ) {
    }

    @Schema(description = "처리 사유 요청")
    public record ReasonRequest(
            @Size(max = 200, message = "사유는 200자 이하로 입력해주세요.")
            String reason
    ) {
    }

    @Schema(description = "회원 계정 정지 응답")
    public record SuspendUserResponse(
            long userId,
            String status,
            LocalDateTime suspendedAt,
            String message
    ) {
    }

    @Schema(description = "주문 강제 취소 응답")
    public record CancelOrderResponse(
            long orderId,
            String status,
            LocalDateTime canceledAt,
            String message
    ) {
    }

    @Schema(description = "알림 무시 처리 응답")
    public record IgnoreAlertResponse(
            long alertId,
            String status,
            LocalDateTime ignoredAt,
            String message
    ) {
    }

    @Schema(description = "조회 개수 query parameter")
    public record LimitQuery(
            @Min(1)
            @Max(100)
            int limit
    ) {
    }
}
