package com.mock.maesoongan.orderservice.portfolio;

import com.mock.maesoongan.orderservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.AvailableCashResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ContestAccountResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingDetailResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.PortfolioSummaryResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ProfitHistoryItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.SeedMoneyResetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PortfolioControllerTest {

    private PortfolioService portfolioService;
    private CurrentMemberProvider currentMemberProvider;
    private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        currentMemberProvider = mock(CurrentMemberProvider.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        mockMvc = standaloneSetup(new PortfolioController(portfolioService, currentMemberProvider, jdbcTemplate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSummaryReturnsPortfolioSummary() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getSummary(7L)).thenReturn(new PortfolioSummaryResponse(
                new BigDecimal("1200000"),
                new BigDecimal("1000000"),
                new BigDecimal("800000"),
                new BigDecimal("200000"),
                new BigDecimal("200000"),
                new BigDecimal("50000"),
                new BigDecimal("5.00")
        ));

        mockMvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAsset", is(1200000)))
                .andExpect(jsonPath("$.data.reservedAmount", is(200000)));
    }

    @Test
    void getAvailableCashReturnsCashInfo() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getAvailableCash(7L)).thenReturn(new AvailableCashResponse(
                new BigDecimal("1000000"),
                new BigDecimal("750000"),
                new BigDecimal("250000")
        ));

        mockMvc.perform(get("/api/portfolio/available-cash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance", is(750000)))
                .andExpect(jsonPath("$.data.reservedAmount", is(250000)));
    }

    @Test
    void resetSeedMoneyReturnsResetResult() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.resetSeedMoney(any(Long.class), any())).thenReturn(new SeedMoneyResetResponse(
                new BigDecimal("10000000"),
                new BigDecimal("12000000"),
                new BigDecimal("10000000"),
                new BigDecimal("10000000"),
                0L,
                2,
                LocalDate.of(2026, 6, 10),
                LocalDateTime.of(2026, 6, 10, 10, 0),
                LocalDateTime.of(2026, 6, 11, 0, 0)
        ));

        mockMvc.perform(post("/api/portfolio/seed-money/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contestId": 0,
                                  "holdingsAndCashResetAgreed": true,
                                  "irreversibleAgreed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seedMoney", is(10000000)))
                .andExpect(jsonPath("$.data.canceledOrderCount", is(2)));
    }

    @Test
    void getHoldingsReturnsHoldingItems() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getHoldings(7L, 0L)).thenReturn(List.of(new HoldingItem(
                "005930",
                "\uC0BC\uC131\uC804\uC790",
                10L,
                new BigDecimal("70000"),
                new BigDecimal("80000"),
                new BigDecimal("800000"),
                new BigDecimal("100000"),
                new BigDecimal("14.2857")
        )));

        mockMvc.perform(get("/api/portfolio/holdings").param("contestId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stockCode", is("005930")))
                .andExpect(jsonPath("$.data[0].valuation", is(800000)));
    }

    @Test
    void getHoldingReturnsHoldingDetail() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getHolding(7L, "005930", 0L)).thenReturn(new HoldingDetailResponse(
                "005930",
                10L,
                8L,
                new BigDecimal("70000")
        ));

        mockMvc.perform(get("/api/portfolio/holdings/{stockCode}", "005930").param("contestId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity", is(10)))
                .andExpect(jsonPath("$.data.availableQuantity", is(8)));
    }

    @Test
    void getProfitHistoryReturnsHistoryItems() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getProfitHistory(7L, "1M")).thenReturn(List.of(new ProfitHistoryItem(
                LocalDate.of(2026, 6, 10),
                new BigDecimal("5.50"),
                new BigDecimal("10550000")
        )));

        mockMvc.perform(get("/api/portfolio/profit-history").param("period", "1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].profitRate", is(5.50)))
                .andExpect(jsonPath("$.data[0].totalAsset", is(10550000)));
    }

    @Test
    void getContestAccountReturnsContestAccount() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getContestAccount(7L, 3L)).thenReturn(new ContestAccountResponse(
                3L,
                new BigDecimal("10000000"),
                new BigDecimal("11000000"),
                new BigDecimal("8000000"),
                new BigDecimal("7000000"),
                new BigDecimal("1000000"),
                new BigDecimal("1000000"),
                new BigDecimal("10.00"),
                5,
                100L
        ));

        mockMvc.perform(get("/api/contests/{contestId}/account", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestId", is(3)))
                .andExpect(jsonPath("$.data.rank", is(5)));
    }

    @Test
    void getContestAccountReturnsNotFoundWhenAccountDoesNotExist() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getContestAccount(7L, 999L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Contest account not found"));

        mockMvc.perform(get("/api/contests/{contestId}/account", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }
}
