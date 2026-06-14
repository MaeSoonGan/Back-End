package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisOrderbookClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketOrderbookResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusType;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.stock.StockNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketOrderbookServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private KisOrderbookClient kisOrderbookClient;
    private MarketStatusService marketStatusService;
    private MarketOrderbookService marketOrderbookService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        kisOrderbookClient = mock(KisOrderbookClient.class);
        marketStatusService = mock(MarketStatusService.class);
        marketOrderbookService = new MarketOrderbookService(
                redisTemplate,
                objectMapper,
                kisOrderbookClient,
                new StockNameResolver(false, null),
                marketStatusService,
                300
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(marketStatusService.currentStatus()).thenReturn(marketStatus(MarketStatusType.OPEN));
    }

    @Test
    void findOrderbookReturnsCachedRealtimeEvent() throws Exception {
        when(valueOperations.get("stock:005930:orderbook"))
                .thenReturn(objectMapper.writeValueAsString(orderbookEvent("005930", "Samsung")));

        Optional<MarketOrderbookResponse> response = marketOrderbookService.findOrderbook("005930");

        assertThat(response).isPresent();
        assertThat(response.get().stockCode()).isEqualTo("005930");
        assertThat(response.get().asks().get(0).price()).isEqualByComparingTo("75500");
    }

    @Test
    void findOrderbookFetchesFromKisAndCachesWhenCacheIsMissing() {
        when(valueOperations.get("stock:005930:orderbook")).thenReturn(null);
        when(kisOrderbookClient.fetchOrderbook("005930")).thenReturn(Optional.of(kisOrderbook("Samsung")));

        Optional<MarketOrderbookResponse> response = marketOrderbookService.findOrderbook("005930");

        assertThat(response).isPresent();
        assertThat(response.get().stockName()).isEqualTo("Samsung");
        assertThat(response.get().bids().get(0).price()).isEqualByComparingTo("75400");
        verify(valueOperations).set(eq("stock:005930:orderbook"), anyString(), eq(Duration.ofSeconds(300)));
        verify(valueOperations).set(eq("stock:005930:orderbook:last-close"), anyString());
    }

    @Test
    void findOrderbookFallsBackToLastCloseWithoutKisWhenMarketIsClosed() throws Exception {
        when(valueOperations.get("stock:005930:orderbook")).thenReturn(null);
        when(marketStatusService.currentStatus()).thenReturn(marketStatus(MarketStatusType.CLOSED));
        when(valueOperations.get("stock:005930:orderbook:last-close"))
                .thenReturn(objectMapper.writeValueAsString(orderbookEvent("005930", "Samsung")));

        Optional<MarketOrderbookResponse> response = marketOrderbookService.findOrderbook("005930");

        assertThat(response).isPresent();
        assertThat(response.get().stockCode()).isEqualTo("005930");
        assertThat(response.get().stockName()).isEqualTo("Samsung");
        assertThat(response.get().asks().get(0).price()).isEqualByComparingTo("75500");
        verifyNoInteractions(kisOrderbookClient);
    }

    @Test
    void findOrderbookReturnsEmptyWhenCacheAndKisAreMissing() {
        when(valueOperations.get("stock:999999:orderbook")).thenReturn(null);
        when(kisOrderbookClient.fetchOrderbook("999999")).thenReturn(Optional.empty());
        when(valueOperations.get("stock:999999:orderbook:last-close")).thenReturn(null);

        Optional<MarketOrderbookResponse> response = marketOrderbookService.findOrderbook("999999");

        assertThat(response).isEmpty();
    }

    @Test
    void lastCloseOrderbookKeyUsesExpectedRedisKeyFormat() {
        assertThat(MarketOrderbookService.lastCloseOrderbookKey("005930"))
                .isEqualTo("stock:005930:orderbook:last-close");
    }

    private OrderbookQuoteEvent orderbookEvent(String stockCode, String stockName) {
        return OrderbookQuoteEvent.of(
                stockCode,
                stockName,
                List.of(new OrderbookLevel(new BigDecimal("75500"), 100L)),
                List.of(new OrderbookLevel(new BigDecimal("75400"), 200L)),
                LocalDateTime.of(2026, 6, 11, 10, 0),
                LocalDateTime.of(2026, 6, 11, 10, 0),
                1L
        );
    }

    private KisOrderbookClient.KisOrderbookSnapshot kisOrderbook(String stockName) {
        return new KisOrderbookClient.KisOrderbookSnapshot(
                stockName,
                List.of(new OrderbookLevel(new BigDecimal("75500"), 100L)),
                List.of(new OrderbookLevel(new BigDecimal("75400"), 200L)),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }

    private MarketStatusResponse marketStatus(MarketStatusType status) {
        return new MarketStatusResponse(
                status,
                status.name(),
                LocalTime.of(9, 0),
                LocalTime.of(15, 30),
                LocalTime.of(10, 0),
                status == MarketStatusType.OPEN
        );
    }
}
