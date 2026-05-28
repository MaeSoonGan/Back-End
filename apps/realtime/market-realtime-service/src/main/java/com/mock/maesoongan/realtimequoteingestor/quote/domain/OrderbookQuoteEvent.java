package com.mock.maesoongan.realtimequoteingestor.quote.domain;

import java.time.LocalDateTime;
import java.util.List;

public record OrderbookQuoteEvent(
        QuoteEventType type,
        String code,
        String name,
        List<OrderbookLevel> asks,
        List<OrderbookLevel> bids,
        LocalDateTime sourceTimestamp,
        LocalDateTime receivedAt,
        long sequence
) implements QuoteEvent {

    public OrderbookQuoteEvent {
        if (type == null) {
            type = QuoteEventType.ORDERBOOK;
        }
        if (type != QuoteEventType.ORDERBOOK) {
            throw new IllegalArgumentException("type must be ORDERBOOK");
        }
        validateRequired(code, "code");
        validateRequired(name, "name");
        validateRequired(sourceTimestamp, "sourceTimestamp");
        validateRequired(receivedAt, "receivedAt");
        if (asks == null || asks.isEmpty()) {
            throw new IllegalArgumentException("asks is required");
        }
        if (bids == null || bids.isEmpty()) {
            throw new IllegalArgumentException("bids is required");
        }
        asks = List.copyOf(asks);
        bids = List.copyOf(bids);
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be greater than or equal to 0");
        }
    }

    public static OrderbookQuoteEvent of(
            String code,
            String name,
            List<OrderbookLevel> asks,
            List<OrderbookLevel> bids,
            LocalDateTime sourceTimestamp,
            LocalDateTime receivedAt,
            long sequence
    ) {
        return new OrderbookQuoteEvent(
                QuoteEventType.ORDERBOOK,
                code,
                name,
                asks,
                bids,
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
