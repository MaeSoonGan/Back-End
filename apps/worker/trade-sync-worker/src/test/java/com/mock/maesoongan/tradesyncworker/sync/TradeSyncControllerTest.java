package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock.maesoongan.tradesyncworker.common.GlobalExceptionHandler;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TradeSyncControllerTest {

    private TradeSyncService tradeSyncService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tradeSyncService = mock(TradeSyncService.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = standaloneSetup(new TradeSyncController(tradeSyncService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void healthReturnsWorkerStatus() throws Exception {
        mockMvc.perform(get("/internal/sync/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UP")))
                .andExpect(jsonPath("$.data.service", is("trade-sync-worker")));
    }

    @Test
    void syncOrderReturnsSuccessResult() throws Exception {
        when(tradeSyncService.syncOrder(any())).thenReturn(result("order-event-1", "ORDER_SNAPSHOT_SYNC", "ORDER", "5001", "SUCCESS"));

        mockMvc.perform(post("/internal/sync/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "order-event-1",
                                  "orderId": 5001,
                                  "memberId": 7,
                                  "contestId": 3,
                                  "stockId": 1,
                                  "stockCode": "005930",
                                  "stockName": "Samsung",
                                  "side": "BUY",
                                  "orderType": "LIMIT",
                                  "orderPrice": 75400,
                                  "orderQuantity": 10,
                                  "remainingQuantity": 10,
                                  "status": "PENDING",
                                  "orderedAt": "2026-06-11T10:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId", is("order-event-1")))
                .andExpect(jsonPath("$.data.processStatus", is("SUCCESS")));
    }

    @Test
    void syncOrderReturnsBadRequestWhenRequiredFieldIsMissing() throws Exception {
        mockMvc.perform(post("/internal/sync/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "order-event-1",
                                  "memberId": 7,
                                  "stockCode": "005930"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void syncTradeReturnsSuccessResult() throws Exception {
        when(tradeSyncService.syncTrade(any())).thenReturn(result("trade-event-1", "TRADE_HISTORY_SYNC", "TRADE", "9001", "SUCCESS"));

        mockMvc.perform(post("/internal/sync/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "trade-event-1",
                                  "tradeId": 9001,
                                  "orderId": 5001,
                                  "memberId": 7,
                                  "contestId": 3,
                                  "stockId": 1,
                                  "stockCode": "005930",
                                  "stockName": "Samsung",
                                  "side": "BUY",
                                  "executedPrice": 75400,
                                  "executedQuantity": 10,
                                  "executedAmount": 754000,
                                  "executedAt": "2026-06-11T10:01:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregateType", is("TRADE")))
                .andExpect(jsonPath("$.data.processStatus", is("SUCCESS")));
    }

    @Test
    void syncPortfolioReturnsSuccessResult() throws Exception {
        when(tradeSyncService.syncPortfolio(any())).thenReturn(result("portfolio-event-1", "PORTFOLIO_SNAPSHOT_SYNC", "PORTFOLIO", "7:3", "SUCCESS"));

        mockMvc.perform(post("/internal/sync/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "portfolio-event-1",
                                  "memberId": 7,
                                  "contestId": 3,
                                  "cashBalance": 1000000,
                                  "availableCash": 900000,
                                  "stockEvaluationAmount": 500000,
                                  "totalAsset": 1500000,
                                  "totalBuyAmount": 700000,
                                  "totalSellAmount": 0,
                                  "profitAmount": 100000,
                                  "profitRate": 7.14,
                                  "portfolioVersion": 2,
                                  "onpremUpdatedAt": "2026-06-11T10:02:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregateType", is("PORTFOLIO")))
                .andExpect(jsonPath("$.data.processStatus", is("SUCCESS")));
    }

    private SyncResult result(String eventId, String eventType, String aggregateType, String aggregateId, String status) {
        return new SyncResult(
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                status,
                "Sync completed",
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }
}
