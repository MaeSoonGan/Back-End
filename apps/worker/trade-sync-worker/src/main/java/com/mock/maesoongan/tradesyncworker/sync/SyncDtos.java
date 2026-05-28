package com.mock.maesoongan.tradesyncworker.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SyncDtos {

    private SyncDtos() {
    }

    @Schema(description = "Order snapshot sync request")
    public record OrderSyncRequest(
            @NotBlank(message = "eventId is required")
            String eventId,

            @NotNull(message = "orderId is required")
            Long orderId,

            @NotNull(message = "memberId is required")
            Long memberId,

            Long contestId,

            @NotNull(message = "stockId is required")
            Long stockId,

            @NotBlank(message = "stockCode is required")
            String stockCode,

            String stockName,

            @NotBlank(message = "side is required")
            String side,

            @NotBlank(message = "orderType is required")
            String orderType,

            BigDecimal orderPrice,

            @NotNull(message = "orderQuantity is required")
            @Positive(message = "orderQuantity must be positive")
            Integer orderQuantity,

            @NotNull(message = "remainingQuantity is required")
            @PositiveOrZero(message = "remainingQuantity must be zero or positive")
            Integer remainingQuantity,

            @NotBlank(message = "status is required")
            String status,

            String rejectReason,

            @NotNull(message = "orderedAt is required")
            LocalDateTime orderedAt,

            LocalDateTime updatedAt
    ) {
    }

    @Schema(description = "Trade history sync request")
    public record TradeSyncRequest(
            @NotBlank(message = "eventId is required")
            String eventId,

            @NotNull(message = "tradeId is required")
            Long tradeId,

            @NotNull(message = "orderId is required")
            Long orderId,

            @NotNull(message = "memberId is required")
            Long memberId,

            Long contestId,

            @NotNull(message = "stockId is required")
            Long stockId,

            @NotBlank(message = "stockCode is required")
            String stockCode,

            String stockName,

            @NotBlank(message = "side is required")
            String side,

            @NotNull(message = "executedPrice is required")
            BigDecimal executedPrice,

            @NotNull(message = "executedQuantity is required")
            @Positive(message = "executedQuantity must be positive")
            Integer executedQuantity,

            @NotNull(message = "executedAmount is required")
            BigDecimal executedAmount,

            @NotNull(message = "executedAt is required")
            LocalDateTime executedAt
    ) {
    }

    @Schema(description = "Portfolio snapshot sync request")
    public record PortfolioSyncRequest(
            @NotBlank(message = "eventId is required")
            String eventId,

            @NotNull(message = "memberId is required")
            Long memberId,

            Long contestId,

            @NotNull(message = "cashBalance is required")
            BigDecimal cashBalance,

            @NotNull(message = "availableCash is required")
            BigDecimal availableCash,

            @NotNull(message = "stockEvaluationAmount is required")
            BigDecimal stockEvaluationAmount,

            @NotNull(message = "totalAsset is required")
            BigDecimal totalAsset,

            @NotNull(message = "totalBuyAmount is required")
            BigDecimal totalBuyAmount,

            @NotNull(message = "totalSellAmount is required")
            BigDecimal totalSellAmount,

            BigDecimal profitAmount,
            BigDecimal profitRate,
            String holdingsJson,

            @NotNull(message = "portfolioVersion is required")
            Long portfolioVersion,

            LocalDateTime onpremUpdatedAt
    ) {
    }

    @Schema(description = "Sync processing result")
    public record SyncResult(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String processStatus,
            String message,
            LocalDateTime processedAt
    ) {
    }
}
