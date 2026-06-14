package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock.maesoongan.realtimequoteingestor.common.BusinessException;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisCurrentPriceClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPricesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketPriceServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private KisCurrentPriceClient kisCurrentPriceClient;
    private MarketPriceService marketPriceService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        marketPriceService = new MarketPriceService(redisTemplate, objectMapper, kisCurrentPriceClient, 300);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getPriceReturnsCachedPrice() throws Exception {
        when(valueOperations.get("stock:005930:price")).thenReturn(objectMapper.writeValueAsString(price("005930")));

        MarketPriceResponse response = marketPriceService.getPrice("005930");

        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.currentPrice()).isEqualByComparingTo("75400");
    }

    @Test
    void getPriceThrowsNotFoundWhenCacheIsMissing() {
        when(valueOperations.get("stock:999999:price")).thenReturn(null);
        when(kisCurrentPriceClient.fetchPrice("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marketPriceService.getPrice("999999"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("STOCK_NOT_FOUND");
                });
    }

    @Test
    void getPricesReturnsFoundAndNotFoundCodes() throws Exception {
        when(valueOperations.get("stock:005930:price")).thenReturn(objectMapper.writeValueAsString(price("005930")));
        when(valueOperations.get("stock:999999:price")).thenReturn(null);
        when(kisCurrentPriceClient.fetchPrice("999999")).thenReturn(Optional.empty());

        MarketPricesResponse response = marketPriceService.getPrices("005930, ,999999");

        assertThat(response.prices()).hasSize(1);
        assertThat(response.prices().get(0).stockCode()).isEqualTo("005930");
        assertThat(response.notFound()).containsExactly("999999");
    }

    @Test
    void getPriceFetchesFromKisAndCachesWhenCacheIsMissing() {
        when(valueOperations.get("stock:005930:price")).thenReturn(null);
        when(kisCurrentPriceClient.fetchPrice("005930")).thenReturn(Optional.of(kisPrice("Samsung")));

        MarketPriceResponse response = marketPriceService.getPrice("005930");

        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.stockName()).isEqualTo("Samsung");
        assertThat(response.currentPrice()).isEqualByComparingTo("75400");
        verify(valueOperations).set(eq("stock:005930:price"), anyString(), eq(Duration.ofSeconds(300)));
    }

    @Test
    void getPricesThrowsBadRequestWhenCodesIsBlank() {
        assertThatThrownBy(() -> marketPriceService.getPrices(" "))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("BAD_REQUEST");
                });
    }

    @Test
    void findPriceReturnsEmptyWhenCacheIsBlank() {
        when(valueOperations.get("stock:005930:price")).thenReturn("");

        Optional<MarketPriceResponse> response = marketPriceService.findPrice("005930");

        assertThat(response).isEmpty();
    }

    @Test
    void findPriceThrowsIllegalStateWhenCacheJsonIsInvalid() {
        when(valueOperations.get("stock:005930:price")).thenReturn("{invalid");

        assertThatThrownBy(() -> marketPriceService.findPrice("005930"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize market price");
    }

    @Test
    void priceKeyUsesExpectedRedisKeyFormat() {
        assertThat(MarketPriceService.priceKey("005930")).isEqualTo("stock:005930:price");
    }

    private MarketPriceResponse price(String stockCode) {
        return new MarketPriceResponse(
                stockCode,
                "Samsung",
                new BigDecimal("75400"),
                new BigDecimal("1200"),
                new BigDecimal("1.62"),
                "2",
                123456L,
                new BigDecimal("9300000000"),
                new BigDecimal("76000"),
                new BigDecimal("74000"),
                new BigDecimal("74200"),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }

    private KisCurrentPriceClient.KisPriceSnapshot kisPrice(String stockName) {
        return new KisCurrentPriceClient.KisPriceSnapshot(
                stockName,
                new BigDecimal("75400"),
                new BigDecimal("1200"),
                new BigDecimal("1.62"),
                "+",
                123456L,
                new BigDecimal("9300000000"),
                new BigDecimal("76000"),
                new BigDecimal("74000"),
                new BigDecimal("74200"),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }
}
