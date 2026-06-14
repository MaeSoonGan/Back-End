package com.mock.maesoongan.tradesyncworker.sync;

import com.mock.maesoongan.tradesyncworker.notification.NotificationClient;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.AccountEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.ExecutionConfirmedEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.OrderSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.PortfolioSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.TradeSyncRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
    void syncExecutionConfirmedUsesExistingOrderSnapshotReference() throws Exception {
        mockOrderReference();
        mockUnprocessedEvent();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SyncResult result = tradeSyncService.syncExecutionConfirmed(new ExecutionConfirmedEvent(
                8001L,
                5001L,
                1001L,
                "005930",
                "Samsung",
                "BUY",
                new BigDecimal("75400"),
                10,
                new BigDecimal("754000"),
                new BigDecimal("9663500"),
                new BigDecimal("9663500"),
                10,
                new BigDecimal("75400"),
                LocalDateTime.of(2026, 6, 11, 10, 1)
        ));

        assertThat(result.processStatus()).isEqualTo("SUCCESS");
        assertThat(result.aggregateId()).isEqualTo("8001");
        verify(notificationClient).create(eq(7L), eq("TRADE_FILLED_BUY"), anyString(), anyString(), eq("ORDER"), eq(5001L));
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
    void syncAccountEventUpsertsInitialPortfolioSnapshot() {
        mockUnprocessedEvent();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SyncResult result = tradeSyncService.syncAccountEvent(new AccountEvent(
                "CONTEST_ACCOUNT_CREATED",
                "request-1",
                "SUCCESS",
                7L,
                "testtest",
                3L,
                1001L,
                new BigDecimal("10000000"),
                new BigDecimal("10000000"),
                LocalDateTime.of(2026, 6, 14, 16, 1)
        ));

        assertThat(result.processStatus()).isEqualTo("SUCCESS");
        assertThat(result.eventId()).isEqualTo("CONTEST_ACCOUNT_CREATED:request-1");
        assertThat(result.eventType()).isEqualTo("CONTEST_ACCOUNT_CREATED");
        assertThat(result.aggregateType()).isEqualTo("ACCOUNT");
        assertThat(result.aggregateId()).isEqualTo("1001");
        verify(jdbcTemplate).update(
                contains("insert into portfolio_snapshot"),
                eq(7L),
                eq(3L),
                eq(new BigDecimal("10000000")),
                eq(new BigDecimal("10000000")),
                eq(BigDecimal.ZERO),
                eq(new BigDecimal("10000000")),
                eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO),
                eq("[]"),
                eq(1L),
                eq(LocalDateTime.of(2026, 6, 14, 16, 1))
        );
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

    @SuppressWarnings("unchecked")
    private void mockOrderReference() throws Exception {
        when(jdbcTemplate.queryForObject(contains("from order_snapshot"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> rowMapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("member_id")).thenReturn(7L);
                    when(resultSet.getLong("contest_id")).thenReturn(3L);
                    when(resultSet.getLong("stock_id")).thenReturn(1L);
                    when(resultSet.getString("stock_code")).thenReturn("005930");
                    when(resultSet.getString("stock_name")).thenReturn("Samsung");
                    when(resultSet.getString("side")).thenReturn("BUY");
                    return rowMapper.mapRow(resultSet, 0);
                });
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
