package com.mock.maesoongan.realtimequoteingestor.quote.adapter;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventHandler;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "quote.source", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockQuoteSource implements QuoteSource {

    private static final Map<String, String> STOCK_NAMES = Map.of(
            "005930", "Samsung Electronics",
            "000660", "SK hynix",
            "035420", "NAVER",
            "035720", "Kakao"
    );
    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "005930", new BigDecimal("75400"),
            "000660", new BigDecimal("182500"),
            "035420", new BigDecimal("192000"),
            "035720", new BigDecimal("53000")
    );

    private final long emitIntervalMillis;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong();
    private final List<String> subscribedCodes = new CopyOnWriteArrayList<>();
    private final List<String> subscribedIndexes = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executorService;
    private QuoteEventHandler handler;

    public MockQuoteSource(@Value("${quote.mock.emit-interval-millis:1000}") long emitIntervalMillis) {
        this.emitIntervalMillis = emitIntervalMillis;
    }

    @Override
    public void start(QuoteEventHandler handler) {
        this.handler = handler;
        if (!connected.compareAndSet(false, true)) {
            return;
        }

        executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mock-quote-source");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleAtFixedRate(this::emitSubscribedQuotes, 0, emitIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        connected.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    @Override
    public void subscribe(List<String> stockCodes) {
        stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .filter(code -> !subscribedCodes.contains(code))
                .forEach(subscribedCodes::add);
    }

    @Override
    public void unsubscribe(List<String> stockCodes) {
        stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .forEach(subscribedCodes::remove);
    }

    @Override
    public void subscribeIndexes(List<String> markets) {
        markets.stream()
                .map(this::normalizeMarket)
                .filter(market -> !market.isBlank())
                .distinct()
                .filter(market -> !subscribedIndexes.contains(market))
                .forEach(subscribedIndexes::add);
    }

    @Override
    public void unsubscribeIndexes(List<String> markets) {
        markets.stream()
                .map(this::normalizeMarket)
                .filter(market -> !market.isBlank())
                .forEach(subscribedIndexes::remove);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    private void emitSubscribedQuotes() {
        if (!connected.get() || handler == null) {
            return;
        }

        for (String code : subscribedCodes) {
            long currentSequence = sequence.incrementAndGet();
            LocalDateTime now = LocalDateTime.now();
            PriceQuoteEvent priceEvent = createPriceQuoteEvent(code, currentSequence, now);

            handler.handlePrice(priceEvent);
            handler.handleOrderbook(createOrderbookQuoteEvent(code, priceEvent.price(), currentSequence, now));
        }
        for (String market : subscribedIndexes) {
            long currentSequence = sequence.incrementAndGet();
            LocalDateTime now = LocalDateTime.now();
            handler.handleIndex(createIndexQuoteEvent(market, currentSequence, now));
        }
    }

    private PriceQuoteEvent createPriceQuoteEvent(String code, long currentSequence, LocalDateTime now) {
        BigDecimal basePrice = BASE_PRICES.getOrDefault(code, new BigDecimal("50000"));
        BigDecimal change = BigDecimal.valueOf((currentSequence % 21) - 10).multiply(new BigDecimal("100"));
        BigDecimal price = basePrice.add(change).max(BigDecimal.ONE);
        BigDecimal changeRate = change
                .multiply(new BigDecimal("100"))
                .divide(basePrice, 2, RoundingMode.HALF_UP);
        long volume = 1_000_000L + (currentSequence * 10_000L);

        return PriceQuoteEvent.of(
                code,
                STOCK_NAMES.getOrDefault(code, "Mock Stock " + code),
                price,
                change,
                changeRate,
                volume,
                now,
                now,
                currentSequence
        );
    }

    private OrderbookQuoteEvent createOrderbookQuoteEvent(
            String code,
            BigDecimal price,
            long currentSequence,
            LocalDateTime now
    ) {
        List<OrderbookLevel> asks = new ArrayList<>();
        List<OrderbookLevel> bids = new ArrayList<>();

        for (int level = 1; level <= 10; level++) {
            long quantity = 1_000L + (currentSequence * 7L) + (level * 100L);
            asks.add(new OrderbookLevel(price.add(BigDecimal.valueOf(level * 100L)), quantity));
            bids.add(new OrderbookLevel(price.subtract(BigDecimal.valueOf(level * 100L)).max(BigDecimal.ONE), quantity + 50L));
        }

        return OrderbookQuoteEvent.of(
                code,
                STOCK_NAMES.getOrDefault(code, "Mock Stock " + code),
                asks,
                bids,
                now,
                now,
                currentSequence
        );
    }

    private IndexQuoteEvent createIndexQuoteEvent(String market, long currentSequence, LocalDateTime now) {
        BigDecimal baseValue = switch (market) {
            case "KOSPI" -> new BigDecimal("2800.00");
            case "KOSDAQ" -> new BigDecimal("850.00");
            case "KOSPI200" -> new BigDecimal("380.00");
            default -> new BigDecimal("1000.00");
        };
        BigDecimal change = BigDecimal.valueOf((currentSequence % 21) - 10).multiply(new BigDecimal("0.25"));
        BigDecimal value = baseValue.add(change).max(BigDecimal.ONE);
        BigDecimal changeRate = change
                .multiply(new BigDecimal("100"))
                .divide(baseValue, 2, RoundingMode.HALF_UP);
        long volume = 100_000L + (currentSequence * 1_000L);

        return IndexQuoteEvent.of(
                indexCode(market),
                market,
                value,
                change,
                changeRate,
                volume,
                now,
                now,
                currentSequence
        );
    }

    private String normalizeMarket(String market) {
        if (market == null || market.isBlank()) {
            return "";
        }
        return market.trim().toUpperCase(Locale.ROOT);
    }

    private String indexCode(String market) {
        return switch (market) {
            case "KOSPI" -> "0001";
            case "KOSDAQ" -> "1001";
            default -> market;
        };
    }
}
