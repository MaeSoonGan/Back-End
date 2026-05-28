package com.mock.maesoongan.realtimequoteingestor.quote.application;

import java.time.LocalDateTime;

public record QuoteIngestionStatus(
        String quoteSourceMode,
        IngestionStatus ingestionStatus,
        boolean quoteSourceConnected,
        boolean redisEnabled,
        boolean kafkaEnabled,
        LocalDateTime lastReceivedAt,
        long priceEventCount,
        long orderbookEventCount,
        String lastError,
        LocalDateTime checkedAt
) {
}
