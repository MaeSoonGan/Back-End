package com.mock.maesoongan.tradesyncworker.sync;

import com.mock.maesoongan.tradesyncworker.notification.NotificationClient;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.OrderSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.PortfolioSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.TradeSyncRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeSyncServiceTest {

    private JdbcTemplate jdbcTemplate;
    private PlatformTransactionManager transactionManager;
    private NotificationClient notificationClient;
    private TradeSyncService tradeSyncService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionManager = mock(PlatformTransactionManager.class);
        notificationClient = mock(NotificationClient.class);
        tradeSyncService = new TradeSyncService(jdbcTemplate, transactionManager, notificationClient);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void syncOrderReturnsSkippedWhenEventAlreadySucceeded() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn("SUCCESS");

        SyncResult result = tradeSyncService.syncOrder(orderRequest("CANCELED"));

        assertThat(result.processStatus()).isEqualTo("SKIPPED");
        assertThat(result.message()).isEqualTo("Already processed event");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verify(notificationClient, never()).create(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void syncOrderUpsertsOrderAndNotifiesWhenCanceled() {
        mockUnprocessedEvent();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SyncResult result = tradeSyncService.syncOrder(orderRequest("CANCELED"));

        assertThat(result.processStatus()).isEqualTo("SUCCESS");
        assertThat(result.eventType()).isEqualTo("ORDER_SNAPSHOT_SYNC");
        assertThat(result.aggregateType()).isEqualTo("ORDER");
        assertThat(result.aggregateId()).isEqualTo("5001");
        verify(notificationClient).create(eq(7L), eq("ORDER_CANCELED"), anyString(), anyString(), eq("ORDER"), eq(5001L));
    }

    @Test
    void syncOrderDoesNotNotifyWhenStatusIsNotCanceled() {
        mockUnprocessedEvent();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SyncResult result = tradeSyncService.syncOrder(orderRequest("PENDING"));

        assertThat(result.processStatus()).isEqualTo("SUCCESS");
        verify(notificationClient, never()).create(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void syncTradeUpsertsTradeUpdatesOrderAndNotifiesFilledTrade() {
        mockUnprocessedEvent();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SyncResult result = tradeSyncService.syncTrade(tradeRequest("BUY"));

        assertThat(result.processStatus()).isEqualTo("SUCCESS");
        assertThat(result.eventType()).isEqualTo("TRADE_HISTORY_SYNC");
        assertThat(result.aggregateType()).isEqualTo("TRADE");
        assertThat(result.aggregateId()).isEqualTo("9001");
        verify(notificationClient).create(eq(7L), eq("TRADE_FILLED_BUY"), anyString(), anyString(), eq("ORDER"), eq(5001L));
    }

    @Test
    void syncTradeUsesSellNotificationTypeForSellTrade() {
        mockUnprocessedEvent();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SyncResult result = tradeSyncService.syncTrade(tradeRequest("SELL"));

        assertThat(result.processStatus()).isEqualTo("SUCCESS");
        verify(notificationClient).create(eq(7L), eq("TRADE_FILLED_SELL"), anyString(), anyString(), eq("ORDER"), eq(5001L));
    }

    @Test
    void syncPortfolioUsesDefaultContestIdWhenContestIdIsNull() {
        mockUnprocessedEvent();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SyncResult result = tradeSyncService.syncPortfolio(portfolioRequest(null));

        assertThat(result.processStatus()).isEqualTo("SUCCESS");
        assertThat(result.eventType()).isEqualTo("PORTFOLIO_SNAPSHOT_SYNC");
        assertThat(result.aggregateType()).isEqualTo("PORTFOLIO");
        assertThat(result.aggregateId()).isEqualTo("7:0");
    }

    @Test
    void syncOrderLogsFailedEventAndRethrowsWhenUpsertFails() {
        mockUnprocessedEvent();
        doThrow(new RuntimeException("database failure"))
                .doReturn(1)
                .when(jdbcTemplate)
                .update(anyString(), any(Object[].class));

        assertThatThrownBy(() -> tradeSyncService.syncOrder(orderRequest("PENDING")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("database failure");

        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
        verify(notificationClient, never()).create(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private void mockUnprocessedEvent() {
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate)
                .queryForObject(anyString(), eq(String.class), any(Object[].class));
    }

    private OrderSyncRequest orderRequest(String status) {
        return new OrderSyncRequest(
                "order-event-1",
                5001L,
                7L,
                3L,
                1L,
                "005930",
                "Samsung",
                "BUY",
                "LIMIT",
                new BigDecimal("75400"),
                10,
                10,
                status,
                "user cancel",
                LocalDateTime.of(2026, 6, 11, 10, 0),
                null
        );
    }

    private TradeSyncRequest tradeRequest(String side) {
        return new TradeSyncRequest(
                "trade-event-1",
                9001L,
                5001L,
                7L,
                3L,
                1L,
                "005930",
                "Samsung",
                side,
                new BigDecimal("75400"),
                10,
                new BigDecimal("754000"),
                LocalDateTime.of(2026, 6, 11, 10, 1)
        );
    }

    private PortfolioSyncRequest portfolioRequest(Long contestId) {
        return new PortfolioSyncRequest(
                "portfolio-event-1",
                7L,
                contestId,
                new BigDecimal("1000000"),
                new BigDecimal("900000"),
                new BigDecimal("500000"),
                new BigDecimal("1500000"),
                new BigDecimal("700000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                new BigDecimal("7.14"),
                "{\"005930\":10}",
                2L,
                LocalDateTime.of(2026, 6, 11, 10, 2)
        );
    }
}
