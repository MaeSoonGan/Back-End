package com.mock.maesoongan.adminservice.contest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminContestDtos {

    private AdminContestDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    public record ContestSummaryResponse(
            long totalContestCount,
            long scheduledContestCount,
            long activeContestCount,
            long endedContestCount,
            long canceledContestCount,
            long totalParticipantCount
    ) {
    }

    public record ContestListItem(
            long contestId,
            String title,
            String period,
            BigDecimal seedMoney,
            Integer maxParticipants,
            long participantCount,
            String status,
            boolean isPublic,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    public record ContestDetailResponse(
            long contestId,
            String title,
            String description,
            BigDecimal seedMoney,
            Integer maxParticipants,
            BigDecimal maxOrderAmount,
            BigDecimal maxStockRatio,
            String stockType,
            String profitCriteria,
            boolean isPublic,
            String joinType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String status,
            long participantCount,
            Long adminId,
            String adminName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(description = "Contest create request")
    public record ContestCreateRequest(
            @NotBlank(message = "title is required")
            String title,

            String description,

            @NotNull(message = "seedMoney is required")
            @DecimalMin(value = "0.01", message = "seedMoney must be greater than 0")
            BigDecimal seedMoney,

            Integer maxParticipants,
            BigDecimal maxOrderAmount,
            BigDecimal maxStockRatio,
            String stockType,
            String profitCriteria,
            Boolean isPublic,
            String joinType,

            @NotNull(message = "startAt is required")
            LocalDateTime startAt,

            @NotNull(message = "endAt is required")
            LocalDateTime endAt,

            String status
    ) {
    }

    @Schema(description = "Contest update request")
    public record ContestUpdateRequest(
            @NotBlank(message = "title is required")
            String title,

            String description,

            @NotNull(message = "seedMoney is required")
            @DecimalMin(value = "0.01", message = "seedMoney must be greater than 0")
            BigDecimal seedMoney,

            Integer maxParticipants,
            BigDecimal maxOrderAmount,
            BigDecimal maxStockRatio,
            String stockType,
            String profitCriteria,
            Boolean isPublic,
            String joinType,

            @NotNull(message = "startAt is required")
            LocalDateTime startAt,

            @NotNull(message = "endAt is required")
            LocalDateTime endAt,

            String status
    ) {
    }

    public record ContestMutationResponse(
            long contestId,
            String status,
            String message
    ) {
    }

    public record ContestResultResponse(
            long contestId,
            String title,
            String status,
            long participantCount,
            BigDecimal averageProfitRate,
            BigDecimal highestProfitRate,
            BigDecimal highestTotalAsset,
            List<RankingItem> topRankings
    ) {
    }

    public record RankingStatsResponse(
            long contestId,
            long participantCount,
            long rankedCount,
            long excludedCount,
            long profitCount,
            long lossCount,
            BigDecimal averageProfitRate,
            BigDecimal highestProfitRate,
            BigDecimal lowestProfitRate,
            BigDecimal highestTotalAsset,
            LocalDateTime lastUpdatedAt
    ) {
    }

    public record RankingItem(
            long contestId,
            long memberId,
            String nickname,
            String accountId,
            BigDecimal totalAsset,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            Integer rankNo,
            boolean isExcluded,
            String excludedReason,
            LocalDateTime updatedAt
    ) {
    }

    public record RankingExcludeRequest(
            @NotBlank(message = "reason is required")
            String reason
    ) {
    }

    public record RankingStatusResponse(
            long contestId,
            long memberId,
            boolean isExcluded,
            String message
    ) {
    }
}
