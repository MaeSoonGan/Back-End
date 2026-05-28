package com.mock.maesoongan.orderservice.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    public record PortfolioSummaryResponse(
            BigDecimal totalAsset,
            BigDecimal cashBalance,
            BigDecimal availableBalance,
            BigDecimal stockValuation,
            BigDecimal profitAmount,
            BigDecimal profitRate
    ) {
    }

    public record AvailableCashResponse(BigDecimal availableBalance) {
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
            BigDecimal profitAmount,
            BigDecimal profitRate,
            int rank,
            long totalParticipants,
            BigDecimal availableBalance
    ) {
    }

    public record HoldingSnapshot(String stockCode, long quantity) {
    }

    public record ProfitHistoryResponse(List<ProfitHistoryItem> items) {
    }
}
