package com.mock.maesoongan.realtimequoteingestor.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketPriceResponse(
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal changePrice,
        BigDecimal changeRate,
        String changeSign,
        long volume,
        BigDecimal tradingValue,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        LocalDateTime timestamp
) {
}
