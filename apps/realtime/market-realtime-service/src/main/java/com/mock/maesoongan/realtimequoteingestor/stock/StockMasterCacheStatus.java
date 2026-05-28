package com.mock.maesoongan.realtimequoteingestor.stock;

import java.time.LocalDateTime;

public record StockMasterCacheStatus(
        int totalCount,
        int kospiCount,
        int kosdaqCount,
        LocalDateTime loadedAt
) {
}
