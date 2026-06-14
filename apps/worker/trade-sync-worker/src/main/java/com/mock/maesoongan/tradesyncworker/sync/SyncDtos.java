package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

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
            @JsonAlias("executionId")
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

    @Schema(description = "On-prem execution.confirmed Kafka event")
    public record ExecutionConfirmedEvent(
            @NotNull(message = "executionId is required")
            Long executionId,

            @NotNull(message = "orderId is required")
            Long orderId,

            @NotNull(message = "accountId is required")
            Long accountId,

            @NotBlank(message = "stockCode is required")
            String stockCode,

            String stockName,

            @NotBlank(message = "orderType is required")
            String orderType,

            @NotNull(message = "executedPrice is required")
            BigDecimal executedPrice,

            @NotNull(message = "executedQuantity is required")
            @Positive(message = "executedQuantity must be positive")
            Integer executedQuantity,

            @NotNull(message = "executedAmount is required")
            BigDecimal executedAmount,

            BigDecimal updatedDeposit,
            BigDecimal updatedAvailableBalance,
            Integer holdingQuantity,
            BigDecimal holdingAveragePrice,

            @NotNull(message = "confirmedAt is required")
            LocalDateTime confirmedAt
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

    @Schema(description = "Member command result event from on-premise member service")
    public record MemberCommandResultEvent(
            @NotBlank(message = "eventType is required")
            String eventType,
            @NotBlank(message = "requestId is required")
            String requestId,
            Long memberId,
            @NotBlank(message = "status is required")
            String status,
            String reason,
            MemberCommandPayload payload,
            LocalDateTime occurredAt
    ) {
        public String effectiveEventId() {
            return eventType + ":" + requestId;
        }
    }

    @Schema(description = "Member command result payload")
    public record MemberCommandPayload(
            String loginId,
            String email,
            String nickname,
            String phone,
            String status,
            String profileImageUrl,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(description = "Account event from on-premise ledger service")
    public record AccountEvent(
            @NotBlank(message = "eventType is required")
            String eventType,
            @NotBlank(message = "requestId is required")
            String requestId,
            @NotBlank(message = "status is required")
            String status,
            @NotNull(message = "memberId is required")
            Long memberId,
            String userId,
            @NotNull(message = "contestId is required")
            Long contestId,
            @NotNull(message = "accountId is required")
            Long accountId,
            @NotNull(message = "initialCash is required")
            BigDecimal initialCash,
            @NotNull(message = "availableCash is required")
            BigDecimal availableCash,
            LocalDateTime createdAt
    ) {
        public String effectiveEventId() {
            return eventType + ":" + requestId;
        }
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
