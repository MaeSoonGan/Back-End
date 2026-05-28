package com.mock.maesoongan.realtimequoteingestor.stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockMetadataCache {

    public static final String STOCK_NAMES_KEY = "stock:names";
    public static final String STOCK_META_KEY_PREFIX = "stock:meta:";
    public static final String STOCK_MASTER_STATUS_KEY = "stock:master:status";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public StockMetadataCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveAll(List<StockMetadata> metadata, StockMasterCacheStatus status) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> metadataJsonByKey = new LinkedHashMap<>();

        for (StockMetadata item : metadata) {
            names.put(item.code(), item.name());
            metadataJsonByKey.put(STOCK_META_KEY_PREFIX + item.code(), toJson(item));
        }

        redisTemplate.opsForHash().putAll(STOCK_NAMES_KEY, names);
        redisTemplate.opsForValue().multiSet(metadataJsonByKey);
        redisTemplate.opsForValue().set(STOCK_MASTER_STATUS_KEY, toJson(status));
    }

    public String findName(String stockCode) {
        Object value = redisTemplate.opsForHash().get(STOCK_NAMES_KEY, stockCode);
        return value == null ? null : value.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize stock master cache value", exception);
        }
    }
}
