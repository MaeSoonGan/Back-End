package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisOrderbookClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketOrderbookResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusType;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.stock.StockNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class MarketOrderbookService {

    private static final Logger log = LoggerFactory.getLogger(MarketOrderbookService.class);
    private static final String ORDERBOOK_KEY_PREFIX = "stock:";
    private static final String ORDERBOOK_KEY_SUFFIX = ":orderbook";
    private static final String LAST_CLOSE_ORDERBOOK_KEY_SUFFIX = ":orderbook:last-close";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KisOrderbookClient kisOrderbookClient;
    private final StockNameResolver stockNameResolver;
    private final MarketStatusService marketStatusService;
    private final Duration quoteTtl;
    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");

    public MarketOrderbookService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            KisOrderbookClient kisOrderbookClient,
            StockNameResolver stockNameResolver,
            MarketStatusService marketStatusService,
            @Value("${redis.quote-ttl-seconds:300}") long quoteTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.kisOrderbookClient = kisOrderbookClient;
        this.stockNameResolver = stockNameResolver;
        this.marketStatusService = marketStatusService;
        this.quoteTtl = Duration.ofSeconds(quoteTtlSeconds);
    }

    public Optional<MarketOrderbookResponse> findOrderbook(String stockCode) {
        Optional<MarketOrderbookResponse> cachedOrderbook = findCachedOrderbook(orderbookKey(stockCode));
        if (cachedOrderbook.isPresent()) {
            return cachedOrderbook;
        }
        if (!canRequestLiveOrderbook()) {
            return findCachedOrderbook(lastCloseOrderbookKey(stockCode));
        }
        Optional<MarketOrderbookResponse> fetchedOrderbook = fetchAndCacheOrderbook(stockCode);
        if (fetchedOrderbook.isPresent()) {
            return fetchedOrderbook;
        }
        return findCachedOrderbook(lastCloseOrderbookKey(stockCode));
    }

    private boolean canRequestLiveOrderbook() {
        try {
            MarketStatusType status = marketStatusService.currentStatus().marketStatus();
            return status == MarketStatusType.OPEN || status == MarketStatusType.PRE_MARKET;
        } catch (RuntimeException exception) {
            log.warn("Failed to resolve market status for orderbook fallback. err={}", exception.getMessage());
            return true;
        }
    }

    private Optional<MarketOrderbookResponse> findCachedOrderbook(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MarketOrderbookResponse.from(objectMapper.readValue(json, OrderbookQuoteEvent.class)));
        } catch (JsonProcessingException eventException) {
            try {
                return Optional.of(objectMapper.readValue(json, MarketOrderbookResponse.class));
            } catch (JsonProcessingException responseException) {
                throw new IllegalStateException("Failed to deserialize market orderbook", responseException);
            }
        }
    }

    private Optional<MarketOrderbookResponse> fetchAndCacheOrderbook(String stockCode) {
        Optional<MarketOrderbookResponse> response = kisOrderbookClient.fetchOrderbook(stockCode)
                .map(snapshot -> toResponse(stockCode, snapshot));
        response.ifPresent(this::cacheOrderbook);
        return response;
    }

    private MarketOrderbookResponse toResponse(String stockCode, KisOrderbookClient.KisOrderbookSnapshot snapshot) {
        return new MarketOrderbookResponse(
                stockCode,
                stockNameResolver.resolve(stockCode, snapshot.stockName()),
                snapshot.asks(),
                snapshot.bids(),
                snapshot.timestamp()
        );
    }

    private void cacheOrderbook(MarketOrderbookResponse response) {
        try {
            OrderbookQuoteEvent event = OrderbookQuoteEvent.of(
                    response.stockCode(),
                    response.stockName(),
                    response.asks(),
                    response.bids(),
                    response.timestamp(),
                    LocalDateTime.now(zoneId).withNano(0),
                    0L
            );
            redisTemplate.opsForValue().set(
                    orderbookKey(response.stockCode()),
                    objectMapper.writeValueAsString(event),
                    quoteTtl
            );
            redisTemplate.opsForValue().set(
                    lastCloseOrderbookKey(response.stockCode()),
                    objectMapper.writeValueAsString(event)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize market orderbook", exception);
        } catch (RuntimeException exception) {
            log.warn("Failed to cache market orderbook. code={}, err={}", response.stockCode(), exception.getMessage());
        }
    }

    public static String orderbookKey(String stockCode) {
        return ORDERBOOK_KEY_PREFIX + stockCode + ORDERBOOK_KEY_SUFFIX;
    }

    public static String lastCloseOrderbookKey(String stockCode) {
        return ORDERBOOK_KEY_PREFIX + stockCode + LAST_CLOSE_ORDERBOOK_KEY_SUFFIX;
    }
}
