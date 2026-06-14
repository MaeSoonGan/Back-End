package com.mock.maesoongan.realtimequoteingestor.quote.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketOrderbookService;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceMapper;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceService;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteCacheWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "redis", name = "enabled", havingValue = "true")
public class RedisQuoteCacheWriter implements QuoteCacheWriter {

    static final String ORDERBOOK_KEY_PREFIX = "stock:";
    static final String ORDERBOOK_KEY_SUFFIX = ":orderbook";
    static final String INDEX_KEY_PREFIX = "market:index:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MarketPriceMapper marketPriceMapper;
    private final Duration quoteTtl;

    public RedisQuoteCacheWriter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MarketPriceMapper marketPriceMapper,
            @Value("${redis.quote-ttl-seconds:30}") long quoteTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.marketPriceMapper = marketPriceMapper;
        this.quoteTtl = Duration.ofSeconds(quoteTtlSeconds);
    }

    @Override
    public void savePrice(PriceQuoteEvent event) {
        redisTemplate.opsForValue().set(
                MarketPriceService.priceKey(event.code()),
                toJson(marketPriceMapper.from(event)),
                quoteTtl
        );
    }

    @Override
    public void saveOrderbook(OrderbookQuoteEvent event) {
        String json = toJson(event);
        redisTemplate.opsForValue().set(orderbookKey(event.code()), json, quoteTtl);
        redisTemplate.opsForValue().set(MarketOrderbookService.lastCloseOrderbookKey(event.code()), json);
    }

    @Override
    public void saveIndex(IndexQuoteEvent event) {
        redisTemplate.opsForValue().set(indexKey(event.name()), toJson(MarketIndexResponse.from(event)), quoteTtl);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    static String orderbookKey(String code) {
        return ORDERBOOK_KEY_PREFIX + code + ORDERBOOK_KEY_SUFFIX;
    }

    static String indexKey(String market) {
        return INDEX_KEY_PREFIX + market;
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize quote event", exception);
        }
    }
}
