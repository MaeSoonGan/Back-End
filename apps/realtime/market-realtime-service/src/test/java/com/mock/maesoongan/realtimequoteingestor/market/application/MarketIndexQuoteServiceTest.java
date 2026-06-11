package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisIndexClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketIndexQuoteServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private KisIndexClient kisIndexClient;
    private MarketIndexQuoteService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        kisIndexClient = mock(KisIndexClient.class);
        service = new MarketIndexQuoteService(redisTemplate, objectMapper, kisIndexClient, 30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void refreshIndicesCachesFetchedKospiAndKosdaq() {
        when(kisIndexClient.fetchIndex("KOSPI")).thenReturn(Optional.of(index("KOSPI")));
        when(kisIndexClient.fetchIndex("KOSDAQ")).thenReturn(Optional.of(index("KOSDAQ")));

        service.refreshIndices();

        verify(valueOperations).set(eq("market:index:KOSPI"), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOperations).set(eq("market:index:KOSDAQ"), anyString(), eq(Duration.ofSeconds(30)));
    }

    @Test
    void getIndicesReturnsCachedIndices() throws Exception {
        when(valueOperations.get("market:index:KOSPI")).thenReturn(objectMapper.writeValueAsString(index("KOSPI")));
        when(valueOperations.get("market:index:KOSDAQ")).thenReturn(objectMapper.writeValueAsString(index("KOSDAQ")));

        List<MarketIndexResponse> indices = service.getIndices();

        assertThat(indices).extracting(MarketIndexResponse::market).containsExactly("KOSPI", "KOSDAQ");
    }

    @Test
    void getIndicesSkipsMissingCacheValues() throws Exception {
        when(valueOperations.get("market:index:KOSPI")).thenReturn(objectMapper.writeValueAsString(index("KOSPI")));
        when(valueOperations.get("market:index:KOSDAQ")).thenReturn(null);

        List<MarketIndexResponse> indices = service.getIndices();

        assertThat(indices).hasSize(1);
        assertThat(indices.get(0).market()).isEqualTo("KOSPI");
    }

    @Test
    void getIndicesThrowsIllegalStateWhenJsonIsInvalid() {
        when(valueOperations.get("market:index:KOSPI")).thenReturn("{invalid");

        assertThatThrownBy(service::getIndices)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize market index");
    }

    private MarketIndexResponse index(String market) {
        return new MarketIndexResponse(
                market,
                new BigDecimal("2850.10"),
                new BigDecimal("10.10"),
                new BigDecimal("0.36"),
                123456L,
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }
}
