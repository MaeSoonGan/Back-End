package com.mock.maesoongan.adminservice.system;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminSystemDtos {

    private AdminSystemDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    @Schema(description = "Admin monitoring screen response")
    public record MonitoringResponse(
            long todayOrders,
            long todayCompletedOrders,
            long activeUsers,
            long abnormalAlertCount,
            MaintenanceResponse maintenance,
            List<MonitoringAlertItem> alerts,
            LocalDateTime lastUpdatedAt
    ) {
    }

    @Schema(description = "Active user count response")
    public record ActiveUsersResponse(
            long activeUsers
    ) {
    }

    @Schema(description = "Monitoring alert item")
    public record MonitoringAlertItem(
            long alertId,
            String type,
            String targetType,
            Long targetId,
            Long memberId,
            String memberName,
            Long orderId,
            String title,
            String content,
            String status,
            LocalDateTime detectedAt
    ) {
    }

    @Schema(description = "Ignore monitoring alert request")
    public record IgnoreAlertRequest(
            String reason
    ) {
    }

    @Schema(description = "Alert mutation response")
    public record AlertMutationResponse(
            long alertId,
            String status,
            String message
    ) {
    }

    @Schema(description = "Maintenance mode response")
    public record MaintenanceResponse(
            String status,
            boolean enabled,
            String message,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(description = "Maintenance mode update request")
    public record MaintenanceUpdateRequest(
            String status,
            String message
    ) {
    }

    @Schema(description = "Audit log summary response")
    public record AuditLogSummaryResponse(
            long totalLogCount,
            long todayLogCount,
            long monthLogCount,
            long adminCount
    ) {
    }

    @Schema(description = "Audit log item")
    public record AuditLogItem(
            long logId,
            String type,
            String action,
            String targetType,
            Long targetId,
            String detail,
            Long adminId,
            String adminName,
            String ipAddress,
            LocalDateTime createdAt
    ) {
    }

    @Schema(description = "Admin list item")
    public record AdminListItem(
            long adminId,
            String loginId,
            String nickname,
            String email,
            String role,
            String status
    ) {
    }
}
