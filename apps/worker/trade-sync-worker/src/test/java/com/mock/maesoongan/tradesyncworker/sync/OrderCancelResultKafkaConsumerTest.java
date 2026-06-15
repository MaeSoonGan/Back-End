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

class OrderCancelResultKafkaConsumerTest {

    private TradeSyncService tradeSyncService;
    private OrderCancelResultKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tradeSyncService = mock(TradeSyncService.class);
        consumer = new OrderCancelResultKafkaConsumer(objectMapper, tradeSyncService);
    }

    @Test
    void consumeOrderCancelResultParsesPayloadAndSyncsCancelResult() {
        when(tradeSyncService.syncOrderCancelResult(argThat(event -> event != null && event.orderId().equals(5001L))))
                .thenReturn(new SyncResult(
                        "order-cancel-confirmed:5001",
                        "ORDER_CANCEL_CONFIRMED",
                        "ORDER",
                        "5001",
                        "SUCCESS",
                        "Sync completed",
                        LocalDateTime.of(2026, 6, 15, 11, 20)
                ));

        consumer.consumeOrderCancelResult("""
                {
                  "eventType": "ORDER_CANCEL_CONFIRMED",
                  "eventId": "order-cancel-confirmed:5001",
                  "orderId": 5001,
                  "accountId": 1001,
                  "stockCode": "005930",
                  "stockName": "Samsung",
                  "side": "BUY",
                  "orderType": "LIMIT",
                  "orderPrice": 75400,
                  "orderQuantity": 10,
                  "remainingQuantity": 0,
                  "canceledQuantity": 10,
                  "releasedAmount": 754000,
                  "updatedDeposit": 10000000,
                  "updatedAvailableBalance": 10000000,
                  "status": "CANCELED",
                  "reason": null,
                  "confirmedAt": [2026, 6, 15, 11, 20, 0]
                }
                """);

        verify(tradeSyncService).syncOrderCancelResult(argThat(event ->
                event.eventType().equals("ORDER_CANCEL_CONFIRMED")
                        && event.eventId().equals("order-cancel-confirmed:5001")
                        && event.orderId().equals(5001L)
                        && event.accountId().equals(1001L)
                        && event.status().equals("CANCELED")
                        && event.confirmedAt().equals(LocalDateTime.of(2026, 6, 15, 11, 20))
        ));
    }

    @Test
    void consumeOrderCancelResultThrowsIllegalArgumentExceptionWhenPayloadIsInvalid() {
        assertThatThrownBy(() -> consumer.consumeOrderCancelResult("{invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid order.cancel.result event payload");
    }
}
