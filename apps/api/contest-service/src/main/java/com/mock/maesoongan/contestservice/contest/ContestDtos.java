package com.mock.maesoongan.contestservice.contest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ContestDtos {

    private ContestDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    @Schema(description = "Contest list item")
    public record ContestListItem(
            long contestId,
            String title,
            String description,
            BigDecimal seedMoney,
            Integer maxParticipants,
            long participantCount,
            String status,
            boolean joined,
            boolean joinable,
            String joinDisabledReason,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    @Schema(description = "My contest list item (with my stats)")
    public record MyContestListItem(
            long contestId,
            String title,
            String status,
            BigDecimal seedMoney,
            long participantCount,
            Integer myRank,
            BigDecimal currentAsset,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            List<TopRankerItem> topRankers,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    @Schema(description = "Top ranker item")
    public record TopRankerItem(
            Integer rank,
            String nickname,
            BigDecimal profitRate
    ) {
    }

    @Schema(description = "Contest detail response")
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
            String status,
            long participantCount,
            boolean joined,
            boolean joinable,
            String joinDisabledReason,
            BigDecimal myTotalAsset,
            BigDecimal myProfitAmount,
            BigDecimal myProfitRate,
            Integer myRank,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    @Schema(description = "Contest join response")
    public record ContestJoinResponse(
            long contestId,
            long memberId,
            String status,
            String accountProvisionStatus,
            String message
    ) {
    }

    @Schema(description = "Contest stock item")
    public record ContestStockItem(
            long stockId,
            String code,
            String name,
            String market,
            String category,
            String status
    ) {
    }

    @Schema(description = "Ranking item")
    public record RankingItem(
            long memberId,
            String nickname,
            Integer rank,
            BigDecimal totalAsset,
            BigDecimal profitAmount,
            BigDecimal profitRate
    ) {
    }

    @Schema(description = "My ranking response")
    public record MyRankingResponse(
            long contestId,
            long memberId,
            boolean joined,
            Integer rank,
            BigDecimal totalAsset,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            boolean excluded,
            String excludedReason
    ) {
    }

    @Schema(description = "Contest result response")
    public record ContestResultResponse(
            long contestId,
            String title,
            LocalDateTime endAt,
            long totalParticipants,
            MyRankingResponse myResult,
            List<RankingItem> rankings,
            PageInfo pagination
    ) {
    }

    public record PageInfo(
            int page,
            int totalPages
    ) {
    }

    @Schema(description = "Internal contest order validation request")
    public record OrderValidationRequest(
            @NotNull(message = "memberId is required")
            Long memberId,

            @NotNull(message = "stockId is required")
            Long stockId,

            @NotNull(message = "orderAmount is required")
            @DecimalMin(value = "0.01", message = "orderAmount must be positive")
            BigDecimal orderAmount,

            BigDecimal stockRatioAfterOrder
    ) {
    }

    @Schema(description = "Internal contest order validation response")
    public record OrderValidationResponse(
            boolean valid,
            String reason,
            long contestId,
            Long memberId,
            Long stockId,
            BigDecimal maxOrderAmount,
            BigDecimal maxStockRatio
    ) {
    }
}
