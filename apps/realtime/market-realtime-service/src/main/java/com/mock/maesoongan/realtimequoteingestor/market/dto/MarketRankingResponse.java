package com.mock.maesoongan.realtimequoteingestor.market.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MarketRankingResponse(
        List<MarketRankingItemResponse> items,
        boolean isCached,
        LocalDateTime updatedAt
) {
}
