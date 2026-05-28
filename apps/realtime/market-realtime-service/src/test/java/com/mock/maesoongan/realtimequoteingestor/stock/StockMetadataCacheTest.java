package com.mock.maesoongan.realtimequoteingestor.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockMetadataCacheTest {

    @Test
    void savesStockMasterWithoutTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mockHashOperations(redisTemplate);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        StockMetadataCache cache = new StockMetadataCache(redisTemplate, objectMapper());

        cache.saveAll(
                List.of(new StockMetadata("005930", "KR7005930003", "삼성전자", "KOSPI")),
                new StockMasterCacheStatus(1, 1, 0, LocalDateTime.of(2026, 5, 28, 10, 0))
        );

        verify(hashOperations).putAll(
                eq(StockMetadataCache.STOCK_NAMES_KEY),
                eq(Map.of("005930", "삼성전자"))
        );
        verify(valueOperations).multiSet(anyMap());
        verify(valueOperations).set(eq(StockMetadataCache.STOCK_MASTER_STATUS_KEY), eq(
                "{\"totalCount\":1,\"kospiCount\":1,\"kosdaqCount\":0,\"loadedAt\":\"2026-05-28T10:00:00\"}"
        ));
    }

    @SuppressWarnings("unchecked")
    private static HashOperations<String, Object, Object> mockHashOperations(StringRedisTemplate redisTemplate) {
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        return hashOperations;
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations(StringRedisTemplate redisTemplate) {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return valueOperations;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
