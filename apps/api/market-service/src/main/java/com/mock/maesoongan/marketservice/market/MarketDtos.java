package com.mock.maesoongan.marketservice.market;

import java.math.BigDecimal;
import java.util.List;

public final class MarketDtos {

    private MarketDtos() {
    }

    public record MarketIndexResponse(
            String market,
            BigDecimal value,
            BigDecimal change,
            BigDecimal changeRate,
            boolean isCached
    ) {
    }

    public record MarketStatusResponse(
            String status,
            String openTime,
            String closeTime,
            String message
    ) {
    }

    public record MarketRankingItem(
            int rank,
            String code,
            String name,
            String market,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            long volume
    ) {
    }

    public record MarketRankingResponse(
            List<MarketRankingItem> items
    ) {
    }
}
