package com.mock.maesoongan.orderservice.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    public record PortfolioSummaryResponse(
            BigDecimal totalAsset,
            BigDecimal cashBalance,
            BigDecimal availableBalance,
            BigDecimal reservedAmount,
            BigDecimal stockValuation,
            BigDecimal profitAmount,
            BigDecimal profitRate
    ) {
    }

    public record AvailableCashResponse(
            BigDecimal cashBalance,
            BigDecimal availableBalance,
            BigDecimal reservedAmount
    ) {
    }

    public record HoldingItem(
            String stockCode,
            String stockName,
            long quantity,
            BigDecimal avgPrice,
            BigDecimal currentPrice,
            BigDecimal valuation,
            BigDecimal profitAmount,
            BigDecimal profitRate
    ) {
    }

    public record HoldingDetailResponse(
            String stockCode,
            long quantity,
            long availableQuantity,
            BigDecimal avgPrice
    ) {
    }

    public record ProfitHistoryItem(
            LocalDate date,
            BigDecimal profitRate,
            BigDecimal totalAsset
    ) {
    }

    public record ContestAccountResponse(
            long contestId,
            BigDecimal seedMoney,
            BigDecimal currentAsset,
            BigDecimal cashBalance,
            BigDecimal availableBalance,
            BigDecimal reservedAmount,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            int rank,
            long totalParticipants
    ) {
    }

    public record HoldingSnapshot(String stockCode, long quantity) {
    }

    public record ProfitHistoryResponse(List<ProfitHistoryItem> items) {
    }

    public record SeedMoneyResetRequest(
            Long contestId,
            boolean holdingsAndCashResetAgreed,
            boolean irreversibleAgreed
    ) {
    }

    public record SeedMoneyResetResponse(
            BigDecimal seedMoney,
            BigDecimal previousTotalAsset,
            BigDecimal cashBalance,
            BigDecimal availableBalance,
            long remainingResetCount,
            int canceledOrderCount,
            LocalDate resetDate,
            LocalDateTime resetAt,
            LocalDateTime nextResetAvailableAt
    ) {
    }
}
