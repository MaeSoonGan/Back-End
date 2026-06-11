package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisIndexClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 코스피/코스닥 지수를 KIS REST로 주기 조회해 Redis에 캐시하고, 조회용으로 제공한다.
 * ws 구독을 쓰지 않으므로 실시간 등록 한도를 소비하지 않는다(실시간 갱신은 아님).
 */
@Service
public class MarketIndexQuoteService {

    private static final String INDEX_KEY_PREFIX = "market:index:";
    private static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KisIndexClient kisIndexClient;
    private final Duration ttl;

    public MarketIndexQuoteService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            KisIndexClient kisIndexClient,
            @Value("${ranking.hts-top-view.cache-ttl-seconds:30}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.kisIndexClient = kisIndexClient;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public void refreshIndices() {
        for (String market : MARKETS) {
            kisIndexClient.fetchIndex(market)
                    .ifPresent(index -> redisTemplate.opsForValue().set(indexKey(market), toJson(index), ttl));
        }
    }

    public List<MarketIndexResponse> getIndices() {
        List<MarketIndexResponse> indices = new ArrayList<>();
        for (String market : MARKETS) {
            String json = redisTemplate.opsForValue().get(indexKey(market));
            if (StringUtils.hasText(json)) {
                indices.add(fromJson(json));
            }
        }
        return indices;
    }

    private String indexKey(String market) {
        return INDEX_KEY_PREFIX + market;
    }

    private String toJson(MarketIndexResponse index) {
        try {
            return objectMapper.writeValueAsString(index);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize market index", exception);
        }
    }

    private MarketIndexResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, MarketIndexResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize market index", exception);
        }
    }
}
