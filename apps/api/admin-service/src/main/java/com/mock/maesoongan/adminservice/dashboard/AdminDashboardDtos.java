package com.mock.maesoongan.adminservice.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDashboardDtos {

    private AdminDashboardDtos() {
    }

    @Schema(description = "Admin dashboard response")
    public record DashboardResponse(
            @Schema(description = "Total joined member count", example = "1234")
            long totalUsers,

            @Schema(description = "Today new member count", example = "12")
            long todayNewUsers,

            @Schema(description = "Today total order count", example = "4821")
            long todayOrders,

            @Schema(description = "Today completed order count", example = "3214")
            long todayCompletedOrders,

            @Schema(description = "Active contest count", example = "2")
            long activeContestCount,

            @Schema(description = "Total participants in active contests", example = "323")
            long activeContestParticipants,

            @Schema(description = "Pending abnormal alert count", example = "2")
            long abnormalAlertCount,

            @Schema(description = "Alerts requiring immediate handling")
            List<AlertSummary> alerts,

            @Schema(description = "Daily order counts for recent 7 days")
            List<DailyOrder> dailyOrders,

            @Schema(description = "Active contest list")
            List<ActiveContestSummary> activeContests,

            @Schema(description = "Recent admin activity list")
            List<ActivitySummary> recentActivities,

            @Schema(description = "Last updated date time")
            LocalDateTime lastUpdatedAt
    ) {
    }

    @Schema(description = "Alert summary")
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

    @Schema(description = "Daily order count")
    public record DailyOrder(
            LocalDate date,
            long orderCount,
            boolean isToday
    ) {
    }

    @Schema(description = "Active contest summary")
    public record ActiveContestSummary(
            long contestId,
            String contestName,
            String period,
            long participantCount,
            String status
    ) {
    }

    @Schema(description = "Recent admin activity")
    public record ActivitySummary(
            long activityId,
            String type,
            String content,
            Long adminId,
            String adminName,
            LocalDateTime createdAt
    ) {
    }
}
