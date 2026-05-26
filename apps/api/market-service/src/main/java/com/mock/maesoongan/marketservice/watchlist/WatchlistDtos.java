package com.mock.maesoongan.marketservice.watchlist;

import java.math.BigDecimal;
import java.util.List;

public final class WatchlistDtos {

    private WatchlistDtos() {
    }

    public record WatchlistResponse(
            int totalCount,
            List<WatchlistItem> items
    ) {
    }

    public record WatchlistItem(
            String code,
            String name,
            String market,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate
    ) {
    }

    public record AddWatchlistResponse(
            String stockCode,
            int totalCount
    ) {
    }

    public record DeleteWatchlistResponse(
            int totalCount
    ) {
    }
}
