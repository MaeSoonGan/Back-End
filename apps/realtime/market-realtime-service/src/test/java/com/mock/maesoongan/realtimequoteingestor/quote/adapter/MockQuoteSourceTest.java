package com.mock.maesoongan.realtimequoteingestor.quote.adapter;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockQuoteSourceTest {

    @Test
    void emitsPriceAndOrderbookForSubscribedStockCodes() throws InterruptedException {
        MockQuoteSource quoteSource = new MockQuoteSource(10);
        CapturingQuoteEventHandler handler = new CapturingQuoteEventHandler();

        try {
            quoteSource.start(handler);
            quoteSource.subscribe(List.of("005930"));

            assertTrue(handler.await(1, TimeUnit.SECONDS));
            assertTrue(quoteSource.isConnected());
            assertNotNull(handler.priceEvent.get());
            assertNotNull(handler.orderbookEvent.get());
            assertEquals("005930", handler.priceEvent.get().code());
            assertEquals("005930", handler.orderbookEvent.get().code());
            assertEquals(10, handler.orderbookEvent.get().asks().size());
            assertEquals(10, handler.orderbookEvent.get().bids().size());
        } finally {
            quoteSource.stop();
        }
    }

    @Test
    void emitsIndexForSubscribedMarkets() throws InterruptedException {
        MockQuoteSource quoteSource = new MockQuoteSource(10);
        CapturingQuoteEventHandler handler = new CapturingQuoteEventHandler(1);

        try {
            quoteSource.start(handler);
            quoteSource.subscribeIndexes(List.of("KOSPI"));

            assertTrue(handler.await(1, TimeUnit.SECONDS));
            assertNotNull(handler.indexEvent.get());
            assertEquals("KOSPI", handler.indexEvent.get().name());
        } finally {
            quoteSource.stop();
        }
    }

    private static class CapturingQuoteEventHandler implements QuoteEventHandler {

        private final CountDownLatch latch;
        private final AtomicReference<PriceQuoteEvent> priceEvent = new AtomicReference<>();
        private final AtomicReference<OrderbookQuoteEvent> orderbookEvent = new AtomicReference<>();
        private final AtomicReference<IndexQuoteEvent> indexEvent = new AtomicReference<>();

        private CapturingQuoteEventHandler() {
            this(2);
        }

        private CapturingQuoteEventHandler(int expectedEvents) {
            this.latch = new CountDownLatch(expectedEvents);
        }

        @Override
        public void handlePrice(PriceQuoteEvent event) {
            priceEvent.set(event);
            latch.countDown();
        }

        @Override
        public void handleOrderbook(OrderbookQuoteEvent event) {
            orderbookEvent.set(event);
            latch.countDown();
        }

        @Override
        public void handleIndex(IndexQuoteEvent event) {
            indexEvent.set(event);
            latch.countDown();
        }

        private boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}
