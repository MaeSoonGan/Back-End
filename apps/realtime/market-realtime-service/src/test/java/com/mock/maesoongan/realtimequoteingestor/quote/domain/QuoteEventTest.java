package com.mock.maesoongan.realtimequoteingestor.quote.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuoteEventTest {

    @Test
    void createsPriceQuoteEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 0);

        PriceQuoteEvent event = PriceQuoteEvent.of(
                "005930",
                "Samsung Electronics",
                new BigDecimal("75400"),
                new BigDecimal("1200"),
                new BigDecimal("1.62"),
                12_300_000L,
                now,
                now.plusNanos(100_000_000),
                1
        );

        assertEquals(QuoteEventType.PRICE, event.type());
        assertEquals("005930", event.code());
        assertEquals(new BigDecimal("75400"), event.price());
        assertEquals(1, event.sequence());
    }

    @Test
    void rejectsInvalidPriceQuoteEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 0);

        assertThrows(IllegalArgumentException.class, () -> PriceQuoteEvent.of(
                "",
                "Samsung Electronics",
                new BigDecimal("75400"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                now,
                now,
                1
        ));

        assertThrows(IllegalArgumentException.class, () -> PriceQuoteEvent.of(
                "005930",
                "Samsung Electronics",
                new BigDecimal("-1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                now,
                now,
                1
        ));
    }

    @Test
    void createsOrderbookQuoteEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 0);

        OrderbookQuoteEvent event = OrderbookQuoteEvent.of(
                "005930",
                "Samsung Electronics",
                List.of(new OrderbookLevel(new BigDecimal("75800"), 3214)),
                List.of(new OrderbookLevel(new BigDecimal("75300"), 6234)),
                now,
                now.plusNanos(100_000_000),
                2
        );

        assertEquals(QuoteEventType.ORDERBOOK, event.type());
        assertEquals("005930", event.code());
        assertEquals(1, event.asks().size());
        assertEquals(1, event.bids().size());
        assertEquals(2, event.sequence());
    }

    @Test
    void rejectsInvalidOrderbookQuoteEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 0);

        assertThrows(IllegalArgumentException.class, () -> OrderbookQuoteEvent.of(
                "005930",
                "Samsung Electronics",
                List.of(),
                List.of(new OrderbookLevel(new BigDecimal("75300"), 6234)),
                now,
                now,
                1
        ));

        assertThrows(IllegalArgumentException.class, () -> new OrderbookLevel(new BigDecimal("-1"), 1));
        assertThrows(IllegalArgumentException.class, () -> new OrderbookLevel(BigDecimal.ONE, -1));
    }
}
