package com.mock.maesoongan.realtimequoteingestor.quote.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IndexQuoteEvent(
        QuoteEventType type,
        String code,
        String name,
        BigDecimal value,
        BigDecimal change,
        BigDecimal changeRate,
        long volume,
        LocalDateTime sourceTimestamp,
        LocalDateTime receivedAt,
        long sequence
) implements QuoteEvent {

    public IndexQuoteEvent {
        if (type == null) {
            type = QuoteEventType.INDEX;
        }
        if (type != QuoteEventType.INDEX) {
            throw new IllegalArgumentException("type must be INDEX");
        }
        validateRequired(code, "code");
        validateRequired(name, "name");
        validateRequired(value, "value");
        validateRequired(change, "change");
        validateRequired(changeRate, "changeRate");
        validateRequired(sourceTimestamp, "sourceTimestamp");
        validateRequired(receivedAt, "receivedAt");
        if (volume < 0) {
            throw new IllegalArgumentException("volume must be greater than or equal to 0");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be greater than or equal to 0");
        }
    }

    public static IndexQuoteEvent of(
            String code,
            String name,
            BigDecimal value,
            BigDecimal change,
            BigDecimal changeRate,
            long volume,
            LocalDateTime sourceTimestamp,
            LocalDateTime receivedAt,
            long sequence
    ) {
        return new IndexQuoteEvent(
                QuoteEventType.INDEX,
                code,
                name,
                value,
                change,
                changeRate,
                volume,
                sourceTimestamp,
                receivedAt,
                sequence
        );
    }

    private static void validateRequired(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value instanceof String string && string.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
