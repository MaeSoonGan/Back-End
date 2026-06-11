package com.mock.maesoongan.realtimequoteingestor.market.api;

import com.mock.maesoongan.realtimequoteingestor.common.BusinessException;
import com.mock.maesoongan.realtimequoteingestor.common.GlobalExceptionHandler;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketIndexQuoteService;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceService;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketRankingService;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketStatusService;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceSummary;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPricesResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketRankingItemResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketRankingResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MarketRestControllerTest {

    private MarketPriceService marketPriceService;
    private MarketStatusService marketStatusService;
    private MarketRankingService marketRankingService;
    private MarketIndexQuoteService marketIndexQuoteService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        marketPriceService = mock(MarketPriceService.class);
        marketStatusService = mock(MarketStatusService.class);
        marketRankingService = mock(MarketRankingService.class);
        marketIndexQuoteService = mock(MarketIndexQuoteService.class);
        mockMvc = standaloneSetup(new MarketRestController(
                        marketPriceService,
                        marketStatusService,
                        marketRankingService,
                        marketIndexQuoteService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void priceReturnsLatestStockPrice() throws Exception {
        when(marketPriceService.getPrice("005930")).thenReturn(price("005930", "Samsung"));

        mockMvc.perform(get("/api/market/price/{stockCode}", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCode", is("005930")))
                .andExpect(jsonPath("$.stockName", is("Samsung")))
                .andExpect(jsonPath("$.currentPrice", is(75400)));
    }

    @Test
    void priceReturnsNotFoundWhenPriceDoesNotExist() throws Exception {
        when(marketPriceService.getPrice("999999"))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "not found"));

        mockMvc.perform(get("/api/market/price/{stockCode}", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("STOCK_NOT_FOUND")));
    }

    @Test
    void pricesReturnsBatchPriceResult() throws Exception {
        when(marketPriceService.getPrices("005930,999999")).thenReturn(new MarketPricesResponse(
                List.of(MarketPriceSummary.from(price("005930", "Samsung"))),
                List.of("999999")
        ));

        mockMvc.perform(get("/api/market/prices").param("codes", "005930,999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prices[0].stockCode", is("005930")))
                .andExpect(jsonPath("$.notFound[0]", is("999999")));
    }

    @Test
    void indicesReturnsCachedMarketIndices() throws Exception {
        when(marketIndexQuoteService.getIndices()).thenReturn(List.of(
                new MarketIndexResponse("KOSPI", new BigDecimal("2850.10"), new BigDecimal("10.10"), new BigDecimal("0.36"), 123456L, LocalDateTime.of(2026, 6, 11, 10, 0)),
                new MarketIndexResponse("KOSDAQ", new BigDecimal("930.22"), new BigDecimal("-3.10"), new BigDecimal("-0.33"), 654321L, LocalDateTime.of(2026, 6, 11, 10, 0))
        ));

        mockMvc.perform(get("/api/market/indices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market", is("KOSPI")))
                .andExpect(jsonPath("$[1].market", is("KOSDAQ")));
    }

    @Test
    void statusReturnsMarketStatus() throws Exception {
        when(marketStatusService.currentStatus()).thenReturn(new MarketStatusResponse(
                MarketStatusType.OPEN,
                "open",
                LocalTime.of(9, 0),
                LocalTime.of(15, 30),
                LocalTime.of(10, 0),
                true
        ));

        mockMvc.perform(get("/api/market/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketStatus", is("OPEN")))
                .andExpect(jsonPath("$.isOrderable", is(true)));
    }

    @Test
    void htsTopViewRankingReturnsCachedRanking() throws Exception {
        when(marketRankingService.getHtsTopViewRanking()).thenReturn(new MarketRankingResponse(
                List.of(new MarketRankingItemResponse(
                        1,
                        "005930",
                        "Samsung",
                        new BigDecimal("75400"),
                        new BigDecimal("1200"),
                        new BigDecimal("1.62"),
                        123456L
                )),
                true,
                LocalDateTime.of(2026, 6, 11, 10, 0)
        ));

        mockMvc.perform(get("/api/market/ranking/hts-top-view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rank", is(1)))
                .andExpect(jsonPath("$.items[0].stockCode", is("005930")))
                .andExpect(jsonPath("$.isCached", is(true)));
    }

    private MarketPriceResponse price(String stockCode, String stockName) {
        return new MarketPriceResponse(
                stockCode,
                stockName,
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
}
