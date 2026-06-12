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

    // 실시간 조회상위 순위 (FE 호환 필드명: stockName/currentPrice 등)
    public record HtsTopViewRankingItem(
            int rank,
            String stockCode,
            String stockName,
            BigDecimal currentPrice,
            BigDecimal changePrice,
            BigDecimal changeRate,
            long volume
    ) {
    }

    public record HtsTopViewRankingResponse(
            List<HtsTopViewRankingItem> items
    ) {
    }

    public record MarketRankingResponse(
            List<MarketRankingItem> items
    ) {
    }
}
