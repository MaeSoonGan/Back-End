package com.mock.maesoongan.realtimequoteingestor.quote.application;

import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceMapper;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
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
        assertEquals(List.of("005930", "000660"), source.subscribedPriceCodes);
        assertTrue(source.subscribedOrderbookCodes.isEmpty());
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
        assertEquals(0, status.indexEventCount());
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
        assertEquals(0, status.indexEventCount());
        assertEquals(event.receivedAt(), status.lastReceivedAt());
    }

    @Test
    void handlesIndexEvent() {
        FakeQuoteSource source = new FakeQuoteSource();
        FakeCacheWriter cacheWriter = new FakeCacheWriter();
        FakeEventPublisher eventPublisher = new FakeEventPublisher();
        QuoteIngestionService service = new QuoteIngestionService(source, cacheWriter, eventPublisher, event -> {
        }, marketPriceMapper(), "mock");
        IndexQuoteEvent event = indexEvent();

        service.handleIndex(event);

        QuoteIngestionStatus status = service.status();
        assertEquals(1, cacheWriter.indexEvents.size());
        assertEquals(1, eventPublisher.indexEvents.size());
        assertEquals(0, status.priceEventCount());
        assertEquals(0, status.orderbookEventCount());
        assertEquals(1, status.indexEventCount());
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

    private static IndexQuoteEvent indexEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 26, 10, 0);

        return IndexQuoteEvent.of(
                "0001",
                "KOSPI",
                new BigDecimal("2847.15"),
                new BigDecimal("15.42"),
                new BigDecimal("0.54"),
                123456L,
                now,
                now.plusNanos(100_000_000),
                3
        );
    }

    private static class FakeQuoteSource implements QuoteSource {

        private boolean connected;
        private QuoteEventHandler handler;
        private List<String> subscribedPriceCodes = List.of();
        private List<String> subscribedOrderbookCodes = List.of();

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
        public void subscribePrices(List<String> stockCodes) {
            this.subscribedPriceCodes = List.copyOf(stockCodes);
        }

        @Override
        public void unsubscribePrices(List<String> stockCodes) {
            this.subscribedPriceCodes = this.subscribedPriceCodes.stream()
                    .filter(code -> !stockCodes.contains(code))
                    .toList();
        }

        @Override
        public void subscribeOrderbooks(List<String> stockCodes) {
            this.subscribedOrderbookCodes = List.copyOf(stockCodes);
        }

        @Override
        public void unsubscribeOrderbooks(List<String> stockCodes) {
            this.subscribedOrderbookCodes = this.subscribedOrderbookCodes.stream()
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
        private final List<IndexQuoteEvent> indexEvents = new ArrayList<>();
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
        public void saveIndex(IndexQuoteEvent event) {
            indexEvents.add(event);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    private static class FakeEventPublisher implements QuoteEventPublisher {

        private final List<PriceQuoteEvent> priceEvents = new ArrayList<>();
        private final List<OrderbookQuoteEvent> orderbookEvents = new ArrayList<>();
        private final List<IndexQuoteEvent> indexEvents = new ArrayList<>();

        @Override
        public void publishPrice(PriceQuoteEvent event) {
            priceEvents.add(event);
        }

        @Override
        public void publishOrderbook(OrderbookQuoteEvent event) {
            orderbookEvents.add(event);
        }

        @Override
        public void publishIndex(IndexQuoteEvent event) {
            indexEvents.add(event);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }
}
