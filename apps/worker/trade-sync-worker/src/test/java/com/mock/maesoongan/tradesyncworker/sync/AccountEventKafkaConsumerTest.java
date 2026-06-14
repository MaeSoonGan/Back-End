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

class AccountEventKafkaConsumerTest {

    private TradeSyncService tradeSyncService;
    private AccountEventKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tradeSyncService = mock(TradeSyncService.class);
        consumer = new AccountEventKafkaConsumer(objectMapper, tradeSyncService);
    }

    @Test
    void consumeAccountEventParsesPayloadAndSyncsAccountEvent() {
        when(tradeSyncService.syncAccountEvent(argThat(event -> event != null && event.accountId().equals(1001L))))
                .thenReturn(new SyncResult(
                        "CONTEST_ACCOUNT_CREATED:request-1",
                        "CONTEST_ACCOUNT_CREATED",
                        "ACCOUNT",
                        "1001",
                        "SUCCESS",
                        "Sync completed",
                        LocalDateTime.of(2026, 6, 14, 16, 1)
                ));

        consumer.consumeAccountEvent("""
                {
                  "eventType": "CONTEST_ACCOUNT_CREATED",
                  "requestId": "request-1",
                  "status": "SUCCESS",
                  "memberId": 7,
                  "userId": "testtest",
                  "contestId": 3,
                  "accountId": 1001,
                  "initialCash": 10000000,
                  "availableCash": 10000000,
                  "createdAt": "2026-06-14T16:01:00"
                }
                """);

        verify(tradeSyncService).syncAccountEvent(argThat(event ->
                event.eventType().equals("CONTEST_ACCOUNT_CREATED")
                        && event.requestId().equals("request-1")
                        && event.memberId().equals(7L)
                        && event.contestId().equals(3L)
                        && event.accountId().equals(1001L)
                        && event.createdAt().equals(LocalDateTime.of(2026, 6, 14, 16, 1))
        ));
    }

    @Test
    void consumeAccountEventThrowsIllegalArgumentExceptionWhenPayloadIsInvalid() {
        assertThatThrownBy(() -> consumer.consumeAccountEvent("{invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid account.event payload");
    }
}
