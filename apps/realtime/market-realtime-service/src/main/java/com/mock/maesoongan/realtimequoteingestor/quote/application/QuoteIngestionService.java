package com.mock.maesoongan.realtimequoteingestor.quote.application;

import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceMapper;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketOrderbookResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketIndexUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketOrderbookUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketPriceUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteCacheWriter;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventHandler;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventPublisher;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class QuoteIngestionService implements QuoteEventHandler {

    private final QuoteSource quoteSource;
    private final QuoteCacheWriter quoteCacheWriter;
    private final QuoteEventPublisher quoteEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MarketPriceMapper marketPriceMapper;
    private final String quoteSourceMode;
    private final AtomicReference<IngestionStatus> ingestionStatus = new AtomicReference<>(IngestionStatus.NOT_STARTED);
    private final AtomicReference<LocalDateTime> lastReceivedAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicLong priceEventCount = new AtomicLong();
    private final AtomicLong orderbookEventCount = new AtomicLong();
    private final AtomicLong indexEventCount = new AtomicLong();

    public QuoteIngestionService(
            QuoteSource quoteSource,
            QuoteCacheWriter quoteCacheWriter,
            QuoteEventPublisher quoteEventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            MarketPriceMapper marketPriceMapper,
            @Value("${quote.source.mode:mock}") String quoteSourceMode
    ) {
        this.quoteSource = quoteSource;
        this.quoteCacheWriter = quoteCacheWriter;
        this.quoteEventPublisher = quoteEventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.marketPriceMapper = marketPriceMapper;
        this.quoteSourceMode = quoteSourceMode;
    }

    public void start(List<String> stockCodes) {
        try {
            quoteSource.start(this);
            quoteSource.subscribe(stockCodes);
            ingestionStatus.set(IngestionStatus.RUNNING);
            lastError.set(null);
        } catch (RuntimeException exception) {
            ingestionStatus.set(IngestionStatus.FAILED);
            lastError.set(rootCauseMessage(exception));
            throw exception;
        }
    }

    public void stop() {
        quoteSource.stop();
        ingestionStatus.set(IngestionStatus.STOPPED);
    }

    public void subscribe(List<String> stockCodes) {
        quoteSource.subscribe(stockCodes);
    }

    public void unsubscribe(List<String> stockCodes) {
        quoteSource.unsubscribe(stockCodes);
    }

    public void subscribeIndexes(List<String> markets) {
        quoteSource.subscribeIndexes(markets);
    }

    public void unsubscribeIndexes(List<String> markets) {
        quoteSource.unsubscribeIndexes(markets);
    }

    @Override
    public void handlePrice(PriceQuoteEvent event) {
        try {
            quoteCacheWriter.savePrice(event);
            quoteEventPublisher.publishPrice(event);
            MarketPriceResponse price = marketPriceMapper.from(event);
            applicationEventPublisher.publishEvent(new MarketPriceUpdatedEvent(price));
            priceEventCount.incrementAndGet();
            lastReceivedAt.set(event.receivedAt());
            lastError.set(null);
        } catch (RuntimeException exception) {
            ingestionStatus.set(IngestionStatus.FAILED);
            lastError.set(rootCauseMessage(exception));
            throw exception;
        }
    }

    @Override
    public void handleOrderbook(OrderbookQuoteEvent event) {
        try {
            quoteCacheWriter.saveOrderbook(event);
            quoteEventPublisher.publishOrderbook(event);
            applicationEventPublisher.publishEvent(new MarketOrderbookUpdatedEvent(MarketOrderbookResponse.from(event)));
            orderbookEventCount.incrementAndGet();
            lastReceivedAt.set(event.receivedAt());
            lastError.set(null);
        } catch (RuntimeException exception) {
            ingestionStatus.set(IngestionStatus.FAILED);
            lastError.set(rootCauseMessage(exception));
            throw exception;
        }
    }

    @Override
    public void handleIndex(IndexQuoteEvent event) {
        try {
            quoteCacheWriter.saveIndex(event);
            quoteEventPublisher.publishIndex(event);
            applicationEventPublisher.publishEvent(new MarketIndexUpdatedEvent(MarketIndexResponse.from(event)));
            indexEventCount.incrementAndGet();
            lastReceivedAt.set(event.receivedAt());
            lastError.set(null);
        } catch (RuntimeException exception) {
            ingestionStatus.set(IngestionStatus.FAILED);
            lastError.set(rootCauseMessage(exception));
            throw exception;
        }
    }

    public QuoteIngestionStatus status() {
        return new QuoteIngestionStatus(
                quoteSourceMode,
                ingestionStatus.get(),
                quoteSource.isConnected(),
                quoteCacheWriter.isEnabled(),
                quoteEventPublisher.isEnabled(),
                lastReceivedAt.get(),
                priceEventCount.get(),
                orderbookEventCount.get(),
                indexEventCount.get(),
                lastError.get(),
                LocalDateTime.now()
        );
    }

    private String rootCauseMessage(RuntimeException exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? exception.getMessage() : cause.getMessage();
    }
}
