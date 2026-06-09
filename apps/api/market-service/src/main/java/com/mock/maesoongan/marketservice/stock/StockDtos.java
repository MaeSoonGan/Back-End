package com.mock.maesoongan.marketservice.stock;

import java.math.BigDecimal;
import java.util.List;

public final class StockDtos {

    private StockDtos() {
    }

    public record StockPriceResponse(
            long stockId,
            String code,
            String name,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            long volume
    ) {
    }

    public record StockDailyInfoResponse(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal prevClose
    ) {
    }

    public record StockOrderbookResponse(
            List<OrderbookLevel> asks,
            List<OrderbookLevel> bids
    ) {
    }

    public record OrderbookLevel(
            BigDecimal price,
            long quantity
    ) {
    }

    public record StockSearchResponse(
            List<StockSearchItem> stocks
    ) {
    }

    public record StockSearchItem(
            long stockId,
            String stockCode,
            String stockName,
            String market,
            BigDecimal currentPrice,
            BigDecimal changeRate,
            String logoUrl,
            boolean isWatchlisted
    ) {
    }
}
