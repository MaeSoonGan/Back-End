package com.mock.maesoongan.realtimequoteingestor.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketPriceSummary(
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal changePrice,
        BigDecimal changeRate,
        String changeSign,
        long volume,
        LocalDateTime timestamp
) {

    public static MarketPriceSummary from(MarketPriceResponse price) {
        return new MarketPriceSummary(
                price.stockCode(),
                price.stockName(),
                price.currentPrice(),
                price.changePrice(),
                price.changeRate(),
                price.changeSign(),
                price.volume(),
                price.timestamp()
        );
    }
}
