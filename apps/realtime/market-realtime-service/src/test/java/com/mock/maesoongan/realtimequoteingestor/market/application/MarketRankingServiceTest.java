package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisHtsTopViewClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketRankingItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketRankingServiceTest {

    @Test
    void refreshesHtsTopViewRankingCache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        KisHtsTopViewClient client = mock(KisHtsTopViewClient.class);
        MarketRankingService service = new MarketRankingService(redisTemplate, objectMapper(), client, 30);

        when(client.fetchTopViewRanking()).thenReturn(List.of(
                new MarketRankingItemResponse(
                        1,
                        "005930",
                        "Samsung Electronics",
                        new BigDecimal("75400"),
                        new BigDecimal("1200"),
                        new BigDecimal("1.62"),
                        12_300_000L
                )
        ));

        assertEquals(1, service.refreshHtsTopViewRanking().items().size());
        verify(valueOperations).set(
                eq(MarketRankingService.HTS_TOP_VIEW_KEY),
                startsWith("{\"items\":[{\"rank\":1"),
                eq(Duration.ofSeconds(30))
        );
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
