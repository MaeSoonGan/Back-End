package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeExecutionKafkaConsumerTest {

    private TradeSyncService tradeSyncService;
    private TradeExecutionKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tradeSyncService = mock(TradeSyncService.class);
        consumer = new TradeExecutionKafkaConsumer(objectMapper, tradeSyncService);
    }

    @Test
    void consumeOnPremExecutionConfirmedParsesPayloadAndSyncsExecutionConfirmed() {
        when(tradeSyncService.syncExecutionConfirmed(argThat(event -> event != null && event.executionId().equals(8001L))))
                .thenReturn(new SyncResult(
                        "execution.confirmed:8001",
                        "TRADE_HISTORY_SYNC",
                        "TRADE",
                        "8001",
                        "SUCCESS",
                        "Sync completed",
                        LocalDateTime.of(2026, 6, 12, 13, 1, 1)
                ));

        consumer.consumeExecutionConfirmed("""
                {
                  "executionId": 8001,
                  "orderId": 990003,
                  "accountId": 1001,
                  "stockCode": "005930",
                  "stockName": "Samsung",
                  "orderType": "BUY",
                  "executedPrice": 336500,
                  "executedQuantity": 1,
                  "executedAmount": 336500,
                  "updatedDeposit": 9663500,
                  "updatedAvailableBalance": 9663500,
                  "holdingQuantity": 1,
                  "holdingAveragePrice": 336500,
                  "confirmedAt": "2026-06-12T13:01:01"
                }
                """);

        verify(tradeSyncService).syncExecutionConfirmed(argThat(event ->
                event.executionId().equals(8001L)
                        && event.orderId().equals(990003L)
                        && event.accountId().equals(1001L)
                        && event.orderType().equals("BUY")
                        && event.confirmedAt().equals(LocalDateTime.of(2026, 6, 12, 13, 1, 1))
        ));
    }

    @Test
    void consumeExecutionConfirmedParsesPayloadAndSyncsTrade() {
        when(tradeSyncService.syncTrade(argThat(request -> request != null && request.tradeId().equals(9001L))))
                .thenReturn(new SyncResult(
                        "trade-event-1",
                        "TRADE_HISTORY_SYNC",
                        "TRADE",
                        "9001",
                        "SUCCESS",
                        "Sync completed",
                        LocalDateTime.of(2026, 6, 11, 10, 1)
                ));

        consumer.consumeExecutionConfirmed("""
                {
                  "eventId": "trade-event-1",
                  "executionId": 9001,
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
                """);

        verify(tradeSyncService).syncTrade(argThat(request ->
                request.tradeId().equals(9001L)
                        && request.orderId().equals(5001L)
                        && request.stockCode().equals("005930")
        ));
    }

    @Test
    void consumeExecutionConfirmedThrowsIllegalArgumentExceptionWhenPayloadIsInvalid() {
        assertThatThrownBy(() -> consumer.consumeExecutionConfirmed("{invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid execution.confirmed event payload");
    }
}
