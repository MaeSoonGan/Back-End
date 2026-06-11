package com.mock.maesoongan.marketservice.market;

import com.mock.maesoongan.marketservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketIndexResponse;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketRankingItem;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MarketControllerTest {

    private MarketService marketService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        marketService = mock(MarketService.class);
        mockMvc = standaloneSetup(new MarketController(marketService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMarketIndexReturnsIndexSnapshot() throws Exception {
        when(marketService.getMarketIndex("KOSPI")).thenReturn(new MarketIndexResponse(
                "KOSPI",
                new BigDecimal("2847.00"),
                new BigDecimal("15.42"),
                new BigDecimal("0.54"),
                false
        ));

        mockMvc.perform(get("/api/market/index").param("market", "KOSPI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.market").value("KOSPI"))
                .andExpect(jsonPath("$.data.value").value(2847.00))
                .andExpect(jsonPath("$.data.isCached").value(false));
    }

    @Test
    void getMarketStatusReturnsTradingHours() throws Exception {
        when(marketService.getMarketStatus()).thenReturn(new MarketStatusResponse(
                "open",
                "09:00",
                "15:30",
                null
        ));

        mockMvc.perform(get("/api/market/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("open"))
                .andExpect(jsonPath("$.data.openTime").value("09:00"))
                .andExpect(jsonPath("$.data.closeTime").value("15:30"));
    }

    @Test
    void getMarketRankingReturnsTopStocks() throws Exception {
        when(marketService.getMarketRanking("\uAC70\uB798\uB300\uAE08")).thenReturn(List.of(
                new MarketRankingItem(
                        1,
                        "005930",
                        "\uC0BC\uC131\uC804\uC790",
                        "KOSPI",
                        new BigDecimal("75400"),
                        new BigDecimal("1200"),
                        new BigDecimal("1.62"),
                        12_300_000L
                )
        ));

        mockMvc.perform(get("/api/market/ranking").param("type", "\uAC70\uB798\uB300\uAE08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].code").value("005930"));
    }
}
