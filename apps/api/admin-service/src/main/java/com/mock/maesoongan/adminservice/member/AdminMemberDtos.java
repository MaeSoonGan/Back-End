package com.mock.maesoongan.adminservice.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminMemberDtos {

    private AdminMemberDtos() {
    }

    public record MemberSummaryResponse(
            long totalCount,
            long activeCount,
            long suspendedCount,
            long todayJoinCount
    ) {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    public record MemberListItem(
            long memberId,
            String nickname,
            String accountId,
            String email,
            String joinDate,
            long contestCount,
            String totalAsset,
            BigDecimal profitRate,
            int loginFailCount,
            String status
    ) {
    }

    public record MemberDetailResponse(
            long memberId,
            String nickname,
            String accountId,
            String email,
            LocalDate joinDate,
            long contestCount,
            BigDecimal totalAsset,
            BigDecimal profitRate,
            int loginFailCount,
            String status,
            List<SuspensionHistoryItem> suspensions,
            List<SeedPaymentHistoryItem> seedPayments
    ) {
    }

    public record MemberSearchItem(
            long memberId,
            String nickname,
            String accountId,
            String email,
            String status
    ) {
    }

    @Schema(description = "Member suspension request")
    public record SuspendMembersRequest(
            @NotEmpty(message = "memberIds is required")
            List<Long> memberIds,

            @NotBlank(message = "reason is required")
            String reason
    ) {
    }

    public record SuspendMembersResponse(
            int requestedCount,
            int suspendedCount,
            int skippedCount,
            int notFoundCount,
            List<Long> suspendedMemberIds,
            List<Long> skippedMemberIds,
            List<Long> notFoundMemberIds,
            String message
    ) {
    }

    public record SuspensionSummaryResponse(
            long totalSuspensionCount,
            long activeSuspensionCount,
            long releasedSuspensionCount,
            long todaySuspensionCount,
            long autoSuspendedCount
    ) {
    }

    public record SuspensionHistoryItem(
            long suspensionId,
            long memberId,
            String nickname,
            String accountId,
            String reason,
            String status,
            Long adminId,
            String adminName,
            LocalDateTime createdAt,
            LocalDateTime releasedAt
    ) {
    }

    public record SuspensionDetailResponse(
            long suspensionId,
            long memberId,
            String nickname,
            String accountId,
            String email,
            String reason,
            String status,
            Long adminId,
            String adminName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long releaseAdminId,
            String releaseAdminName,
            LocalDateTime releasedAt
    ) {
    }

    public record ReleaseSuspensionRequest(
            @NotBlank(message = "reason is required")
            String reason
    ) {
    }

    public record ReleaseSuspensionResponse(
            long suspensionId,
            long memberId,
            String status,
            String memberStatus,
            String message
    ) {
    }

    public record SeedPaymentSummaryResponse(
            BigDecimal totalPaymentAmount,
            BigDecimal todayPaymentAmount,
            long totalPaymentCount,
            long todayPaymentCount,
            BigDecimal monthPaymentAmount,
            long monthPaymentCount
    ) {
    }

    @Schema(description = "Seed money payment request")
    public record SeedPaymentRequest(
            @NotEmpty(message = "memberIds is required")
            List<Long> memberIds,

            @Schema(description = "Contest ID. Use 0 for default mock investment", example = "0")
            Long contestId,

            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be greater than 0")
            BigDecimal amount,

            @NotBlank(message = "reason is required")
            String reason
    ) {
    }

    public record SeedPaymentResponse(
            int requestedCount,
            int succeededCount,
            int notFoundCount,
            List<Long> succeededMemberIds,
            List<Long> notFoundMemberIds,
            long contestId,
            BigDecimal amount,
            String message
    ) {
    }

    public record SeedPaymentHistoryItem(
            long seedHistoryId,
            long memberId,
            String nickname,
            String accountId,
            Long contestId,
            BigDecimal amount,
            String reason,
            String requestStatus,
            Long adminId,
            String adminName,
            LocalDateTime createdAt,
            LocalDateTime processedAt
    ) {
    }
}
