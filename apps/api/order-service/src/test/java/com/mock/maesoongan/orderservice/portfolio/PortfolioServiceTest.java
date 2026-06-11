package com.mock.maesoongan.orderservice.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ContestAccountResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingDetailResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.PortfolioSummaryResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioRepository.ContestAccountRow;
import com.mock.maesoongan.orderservice.portfolio.PortfolioRepository.DailyProfitRow;
import com.mock.maesoongan.orderservice.portfolio.PortfolioRepository.PortfolioRow;
import com.mock.maesoongan.orderservice.portfolio.PortfolioRepository.StockPriceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    private PortfolioRepository portfolioRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PortfolioService portfolioService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        portfolioService = new PortfolioService(portfolioRepository, redisTemplate, new ObjectMapper());
    }

    @Test
    void getSummaryUsesCachedAvailableBalanceAndCalculatesReservedAmount() {
        when(portfolioRepository.findPortfolio(7L, 0L)).thenReturn(Optional.of(portfolioRow()));
        when(valueOperations.get("balance:7:0")).thenReturn("750000");

        PortfolioSummaryResponse response = portfolioService.getSummary(7L);

        assertThat(response.totalAsset()).isEqualByComparingTo("1200000");
        assertThat(response.cashBalance()).isEqualByComparingTo("1000000");
        assertThat(response.availableBalance()).isEqualByComparingTo("750000");
        assertThat(response.reservedAmount()).isEqualByComparingTo("250000");
    }

    @Test
    void getHoldingsParsesHoldingsJsonAndCalculatesProfit() {
        when(portfolioRepository.findPortfolio(7L, 0L)).thenReturn(Optional.of(portfolioRowWithHoldings()));
        when(portfolioRepository.findStockPrice("005930"))
                .thenReturn(Optional.of(new StockPriceRow("005930", "\uC0BC\uC131\uC804\uC790", new BigDecimal("80000"))));
        when(portfolioRepository.findAverageBuyPrice(7L, 0L, "005930"))
                .thenReturn(Optional.of(new BigDecimal("70000")));
        when(portfolioRepository.countPendingSellQuantity(7L, 0L, "005930")).thenReturn(2L);

        List<HoldingItem> response = portfolioService.getHoldings(7L, 0L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).stockCode()).isEqualTo("005930");
        assertThat(response.get(0).valuation()).isEqualByComparingTo("800000");
        assertThat(response.get(0).profitAmount()).isEqualByComparingTo("100000");
    }

    @Test
    void getHoldingReturnsAvailableQuantityAfterPendingSellOrders() {
        when(portfolioRepository.findPortfolio(7L, 0L)).thenReturn(Optional.of(portfolioRowWithHoldings()));
        when(portfolioRepository.countPendingSellQuantity(7L, 0L, "005930")).thenReturn(3L);
        when(portfolioRepository.findStockPrice("005930"))
                .thenReturn(Optional.of(new StockPriceRow("005930", "\uC0BC\uC131\uC804\uC790", new BigDecimal("80000"))));
        when(portfolioRepository.findAverageBuyPrice(7L, 0L, "005930"))
                .thenReturn(Optional.of(new BigDecimal("70000")));

        HoldingDetailResponse response = portfolioService.getHolding(7L, "005930", 0L);

        assertThat(response.quantity()).isEqualTo(10L);
        assertThat(response.availableQuantity()).isEqualTo(7L);
        assertThat(response.avgPrice()).isEqualByComparingTo("70000");
    }

    @Test
    void getProfitHistoryThrowsBadRequestWhenPeriodIsInvalid() {
        assertThatThrownBy(() -> portfolioService.getProfitHistory(7L, "2Y"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("BAD_REQUEST");
                });
    }

    @Test
    void getProfitHistoryReturnsDailySnapshots() {
        when(portfolioRepository.findDailyProfitHistory(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(new DailyProfitRow(
                LocalDate.of(2026, 6, 10),
                new BigDecimal("5.50"),
                new BigDecimal("10550000")
        )));

        var response = portfolioService.getProfitHistory(7L, "1M");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(response.get(0).profitRate()).isEqualByComparingTo("5.50");
    }

    @Test
    void getContestAccountReturnsAccountWithCachedAvailableBalance() {
        when(portfolioRepository.findContestAccount(7L, 3L)).thenReturn(Optional.of(new ContestAccountRow(
                3L,
                new BigDecimal("10000000"),
                new BigDecimal("8000000"),
                new BigDecimal("3000000"),
                new BigDecimal("11000000"),
                new BigDecimal("1000000"),
                new BigDecimal("10.00"),
                5,
                100L,
                new BigDecimal("7000000")
        )));
        when(valueOperations.get("balance:7:3")).thenReturn("6500000");

        ContestAccountResponse response = portfolioService.getContestAccount(7L, 3L);

        assertThat(response.currentAsset()).isEqualByComparingTo("11000000");
        assertThat(response.availableBalance()).isEqualByComparingTo("6500000");
        assertThat(response.reservedAmount()).isEqualByComparingTo("1500000");
        assertThat(response.rank()).isEqualTo(5);
    }

    @Test
    void getContestAccountThrowsNotFoundWhenAccountDoesNotExist() {
        when(portfolioRepository.findContestAccount(7L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getContestAccount(7L, 999L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resetSeedMoneyThrowsBadRequestWhenConfirmationsAreMissing() {
        assertThatThrownBy(() -> portfolioService.resetSeedMoney(7L, new PortfolioDtos.SeedMoneyResetRequest(0L, true, false)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("RESET_CONFIRMATION_REQUIRED");
                });
    }

    private PortfolioRow portfolioRow() {
        return new PortfolioRow(
                7L,
                0L,
                new BigDecimal("1000000"),
                new BigDecimal("800000"),
                new BigDecimal("200000"),
                new BigDecimal("1200000"),
                new BigDecimal("50000"),
                new BigDecimal("5.00"),
                "[]",
                1L,
                LocalDateTime.of(2026, 6, 10, 10, 0)
        );
    }

    private PortfolioRow portfolioRowWithHoldings() {
        return new PortfolioRow(
                7L,
                0L,
                new BigDecimal("1000000"),
                new BigDecimal("800000"),
                new BigDecimal("800000"),
                new BigDecimal("1800000"),
                new BigDecimal("100000"),
                new BigDecimal("14.2857"),
                """
                        [
                          {"stockCode": "005930", "quantity": 10}
                        ]
                        """,
                1L,
                LocalDateTime.of(2026, 6, 10, 10, 0)
        );
    }
}
