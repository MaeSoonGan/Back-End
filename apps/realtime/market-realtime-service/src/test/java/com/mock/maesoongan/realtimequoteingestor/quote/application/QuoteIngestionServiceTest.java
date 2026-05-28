package com.mock.maesoongan.realtimequoteingestor.quote.application;

import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceMapper;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteCacheWriter;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventHandler;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventPublisher;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteSource;
import com.mock.maesoongan.realtimequoteingestor.stock.StockNameResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteIngestionServiceTest {

    @Test
    void startsSourceAndSubscribesStockCodes() {
        FakeQuoteSource source = new FakeQuoteSource();
        FakeCacheWriter cacheWriter = new FakeCacheWriter();
        FakeEventPublisher eventPublisher = new FakeEventPublisher();
        QuoteIngestionService service = new QuoteIngestionService(source, cacheWriter, eventPublisher, event -> {
        }, marketPriceMapper(), "mock");

        service.start(List.of("005930", "000660"));

        QuoteIngestionStatus status = service.status();
        assertEquals(IngestionStatus.RUNNING, status.ingestionStatus());
        assertTrue(status.quoteSourceConnected());
        assertEquals(List.of("005930", "000660"), source.subscribedCodes);
        assertNotNull(source.handler);
        assertFalse(status.redisEnabled());
        assertFalse(status.kafkaEnabled());
    }

    @Test
    void handlesPriceEvent() {
        FakeQuoteSource source = new FakeQuoteSource();
        FakeCacheWriter cacheWriter = new FakeCacheWriter();
        FakeEventPublisher eventPublisher = new FakeEventPublisher();
        QuoteIngestionService service = new QuoteIngestionService(source, cacheWriter, eventPublisher, event -> {
        }, marketPriceMapper(), "mock");
        PriceQuoteEvent event = priceEvent();

        service.handlePrice(event);

        QuoteIngestionStatus status = service.status();
        assertEquals(1, cacheWriter.priceEvents.size());
        assertEquals(1, eventPublisher.priceEvents.size());
        assertEquals(1, status.priceEventCount());
        assertEquals(0, status.orderbookEventCount());
        assertEquals(event.receivedAt(), status.lastReceivedAt());
    }

    @Test
    void handlesOrderbookEvent() {
        FakeQuoteSource source = new FakeQuoteSource();
        FakeCacheWriter cacheWriter = new FakeCacheWriter();
        FakeEventPublisher eventPublisher = new FakeEventPublisher();
        QuoteIngestionService service = new QuoteIngestionService(source, cacheWriter, eventPublisher, event -> {
        }, marketPriceMapper(), "mock");
        OrderbookQuoteEvent event = orderbookEvent();

        service.handleOrderbook(event);

        QuoteIngestionStatus status = service.status();
        assertEquals(1, cacheWriter.orderbookEvents.size());
        assertEquals(1, eventPublisher.orderbookEvents.size());
        assertEquals(0, status.priceEventCount());
        assertEquals(1, status.orderbookEventCount());
        assertEquals(event.receivedAt(), status.lastReceivedAt());
    }

    @Test
    void marksFailedWhenEventHandlingFails() {
        FakeQuoteSource source = new FakeQuoteSource();
        FakeCacheWriter cacheWriter = new FakeCacheWriter();
        cacheWriter.failOnPrice = true;
        FakeEventPublisher eventPublisher = new FakeEventPublisher();
        QuoteIngestionService service = new QuoteIngestionService(source, cacheWriter, eventPublisher, event -> {
        }, marketPriceMapper(), "mock");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.handlePrice(priceEvent())
        );

        QuoteIngestionStatus status = service.status();
        assertEquals("cache failed", exception.getMessage());
        assertEquals(IngestionStatus.FAILED, status.ingestionStatus());
        assertEquals("cache failed", status.lastError());
        assertEquals(0, status.priceEventCount());
        assertEquals(0, eventPublisher.priceEvents.size());
    }

    private static PriceQuoteEvent priceEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 0);

        return PriceQuoteEvent.of(
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
    }

    private static MarketPriceMapper marketPriceMapper() {
        return new MarketPriceMapper(new StockNameResolver(false, null));
    }

    private static OrderbookQuoteEvent orderbookEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 0);

        return OrderbookQuoteEvent.of(
                "005930",
                "Samsung Electronics",
                List.of(new OrderbookLevel(new BigDecimal("75800"), 3214)),
                List.of(new OrderbookLevel(new BigDecimal("75300"), 6234)),
                now,
                now.plusNanos(100_000_000),
                2
        );
    }

    private static class FakeQuoteSource implements QuoteSource {

        private boolean connected;
        private QuoteEventHandler handler;
        private List<String> subscribedCodes = List.of();

        @Override
        public void start(QuoteEventHandler handler) {
            this.connected = true;
            this.handler = handler;
        }

        @Override
        public void stop() {
            this.connected = false;
        }

        @Override
        public void subscribe(List<String> stockCodes) {
            this.subscribedCodes = List.copyOf(stockCodes);
        }

        @Override
        public void unsubscribe(List<String> stockCodes) {
            this.subscribedCodes = this.subscribedCodes.stream()
                    .filter(code -> !stockCodes.contains(code))
                    .toList();
        }

        @Override
        public boolean isConnected() {
            return connected;
        }
    }

    private static class FakeCacheWriter implements QuoteCacheWriter {

        private final List<PriceQuoteEvent> priceEvents = new ArrayList<>();
        private final List<OrderbookQuoteEvent> orderbookEvents = new ArrayList<>();
        private boolean failOnPrice;

        @Override
        public void savePrice(PriceQuoteEvent event) {
            if (failOnPrice) {
                throw new IllegalStateException("cache failed");
            }
            priceEvents.add(event);
        }

        @Override
        public void saveOrderbook(OrderbookQuoteEvent event) {
            orderbookEvents.add(event);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    private static class FakeEventPublisher implements QuoteEventPublisher {

        private final List<PriceQuoteEvent> priceEvents = new ArrayList<>();
        private final List<OrderbookQuoteEvent> orderbookEvents = new ArrayList<>();

        @Override
        public void publishPrice(PriceQuoteEvent event) {
            priceEvents.add(event);
        }

        @Override
        public void publishOrderbook(OrderbookQuoteEvent event) {
            orderbookEvents.add(event);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }
}
