package com.mock.maesoongan.realtimequoteingestor.quote.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceMapper;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.stock.StockNameResolver;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisQuoteCacheWriterTest {

    @Test
    void savesPriceQuoteWithLatestPriceKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        RedisQuoteCacheWriter cacheWriter = new RedisQuoteCacheWriter(redisTemplate, objectMapper(), marketPriceMapper(), 30);

        cacheWriter.savePrice(priceEvent());

        verify(valueOperations).set(
                eq("stock:005930:price"),
                startsWith("{\"stockCode\":\"005930\""),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void savesOrderbookQuoteWithLatestOrderbookKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        RedisQuoteCacheWriter cacheWriter = new RedisQuoteCacheWriter(redisTemplate, objectMapper(), marketPriceMapper(), 30);

        cacheWriter.saveOrderbook(orderbookEvent());

        verify(valueOperations).set(
                eq("stock:005930:orderbook"),
                startsWith("{\"type\":\"ORDERBOOK\""),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void createsStableRedisKeys() {
        assertEquals("stock:005930:orderbook", RedisQuoteCacheWriter.orderbookKey("005930"));
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations(StringRedisTemplate redisTemplate) {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return valueOperations;
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

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
}
