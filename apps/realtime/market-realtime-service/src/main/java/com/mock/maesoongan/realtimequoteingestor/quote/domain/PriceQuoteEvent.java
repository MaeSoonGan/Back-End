package com.mock.maesoongan.realtimequoteingestor.quote.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceQuoteEvent(
        QuoteEventType type,
        String code,
        String name,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changeRate,
        long volume,
        LocalDateTime sourceTimestamp,
        LocalDateTime receivedAt,
        long sequence
) implements QuoteEvent {

    public PriceQuoteEvent {
        if (type == null) {
            type = QuoteEventType.PRICE;
        }
        if (type != QuoteEventType.PRICE) {
            throw new IllegalArgumentException("type must be PRICE");
        }
        validateRequired(code, "code");
        validateRequired(name, "name");
        validateNonNegative(price, "price");
        validateRequired(change, "change");
        validateRequired(changeRate, "changeRate");
        if (volume < 0) {
            throw new IllegalArgumentException("volume must be greater than or equal to 0");
        }
        validateRequired(sourceTimestamp, "sourceTimestamp");
        validateRequired(receivedAt, "receivedAt");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be greater than or equal to 0");
        }
    }

    public static PriceQuoteEvent of(
            String code,
            String name,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            long volume,
            LocalDateTime sourceTimestamp,
            LocalDateTime receivedAt,
            long sequence
    ) {
        return new PriceQuoteEvent(
                QuoteEventType.PRICE,
                code,
                name,
                price,
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

    private static void validateNonNegative(BigDecimal value, String name) {
        validateRequired(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to 0");
        }
    }
}
