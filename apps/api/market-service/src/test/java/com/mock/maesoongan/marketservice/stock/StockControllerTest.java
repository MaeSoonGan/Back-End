package com.mock.maesoongan.marketservice.stock;

import com.mock.maesoongan.marketservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.marketservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.marketservice.stock.StockDtos.OrderbookLevel;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockChartItem;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockChartResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockDailyInfoResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockOrderbookResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockPriceResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockSearchItem;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class StockControllerTest {

    private StockService stockService;
    private StockChartService stockChartService;
    private CurrentMemberProvider currentMemberProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stockService = mock(StockService.class);
        stockChartService = mock(StockChartService.class);
        currentMemberProvider = mock(CurrentMemberProvider.class);
        mockMvc = standaloneSetup(new StockController(stockService, stockChartService, currentMemberProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPriceReturnsStockPrice() throws Exception {
        when(stockService.getPrice("005930")).thenReturn(new StockPriceResponse(
                1L,
                "005930",
                "\uC0BC\uC131\uC804\uC790",
                new BigDecimal("75400"),
                new BigDecimal("1200"),
                new BigDecimal("1.62"),
                12_300_000L
        ));

        mockMvc.perform(get("/api/stocks/005930/price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("005930"))
                .andExpect(jsonPath("$.data.name").value("\uC0BC\uC131\uC804\uC790"))
                .andExpect(jsonPath("$.data.price").value(75400));
    }

    @Test
    void getDailyInfoReturnsLatestDailyInfo() throws Exception {
        when(stockService.getDailyInfo("005930")).thenReturn(new StockDailyInfoResponse(
                new BigDecimal("74200"),
                new BigDecimal("75900"),
                new BigDecimal("74100"),
                new BigDecimal("74200")
        ));

        mockMvc.perform(get("/api/stocks/005930/daily-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.open").value(74200))
                .andExpect(jsonPath("$.data.high").value(75900))
                .andExpect(jsonPath("$.data.low").value(74100))
                .andExpect(jsonPath("$.data.prevClose").value(74200));
    }

    @Test
    void getChartReturnsCandles() throws Exception {
        when(stockChartService.getChart("005930", "D", "1Y", null, null)).thenReturn(new StockChartResponse(
                "005930",
                "D",
                LocalDate.of(2025, 6, 10),
                LocalDate.of(2026, 6, 10),
                List.of(new StockChartItem(
                        LocalDate.of(2026, 6, 10),
                        new BigDecimal("74200"),
                        new BigDecimal("75900"),
                        new BigDecimal("74100"),
                        new BigDecimal("75400"),
                        12_300_000L
                ))
        ));

        mockMvc.perform(get("/api/stocks/005930/chart")
                        .param("period", "D")
                        .param("range", "1Y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("005930"))
                .andExpect(jsonPath("$.data.period").value("D"))
                .andExpect(jsonPath("$.data.items[0].close").value(75400));
    }

    @Test
    void getOrderbookReturnsAskAndBidLevels() throws Exception {
        when(stockService.getOrderbook("005930")).thenReturn(new StockOrderbookResponse(
                List.of(new OrderbookLevel(new BigDecimal("75800"), 3214)),
                List.of(new OrderbookLevel(new BigDecimal("75300"), 6234))
        ));

        mockMvc.perform(get("/api/stocks/005930/orderbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.asks[0].price").value(75800))
                .andExpect(jsonPath("$.data.bids[0].quantity").value(6234));
    }

    @Test
    void searchStocksUsesCurrentMember() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(stockService.search("\uC0BC\uC131", "KOSPI", 7L)).thenReturn(new StockSearchResponse(List.of(
                new StockSearchItem(
                        1L,
                        "005930",
                        "\uC0BC\uC131\uC804\uC790",
                        "KOSPI",
                        new BigDecimal("75400"),
                        new BigDecimal("1.62"),
                        null,
                        true
                )
        )));

        mockMvc.perform(get("/api/stocks/search")
                        .param("keyword", "\uC0BC\uC131")
                        .param("market", "KOSPI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stocks[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.data.stocks[0].isWatchlisted").value(true));

        verify(stockService).search("\uC0BC\uC131", "KOSPI", 7L);
    }
}
