package com.mock.maesoongan.realtimequoteingestor.market.dto;

import java.math.BigDecimal;

public record MarketRankingItemResponse(
        int rank,
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal changePrice,
        BigDecimal changeRate,
        long volume
) {
}
