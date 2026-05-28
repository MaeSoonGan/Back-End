package com.mock.maesoongan.realtimequoteingestor.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StockNameResolver {

    private final boolean redisEnabled;
    private final StockMetadataCache stockMetadataCache;

    public StockNameResolver(
            @Value("${redis.enabled:false}") boolean redisEnabled,
            StockMetadataCache stockMetadataCache
    ) {
        this.redisEnabled = redisEnabled;
        this.stockMetadataCache = stockMetadataCache;
    }

    public String resolve(String stockCode, String fallbackName) {
        if (redisEnabled) {
            try {
                String stockName = stockMetadataCache.findName(stockCode);
                if (StringUtils.hasText(stockName)) {
                    return stockName;
                }
            } catch (RuntimeException ignored) {
                // Quote processing should not fail only because stock metadata lookup failed.
            }
        }
        if (StringUtils.hasText(fallbackName) && !fallbackName.equals(stockCode)) {
            return fallbackName;
        }
        return stockCode;
    }
}
