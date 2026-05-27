package com.mock.maesoongan.tradesyncworker.sync;

import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.OrderSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.PortfolioSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.TradeSyncRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class TradeSyncService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TradeSyncService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public SyncResult syncOrder(OrderSyncRequest request) {
        return processEvent(
                request.eventId(),
                "ORDER_SNAPSHOT_SYNC",
                "ORDER",
                String.valueOf(request.orderId()),
                () -> upsertOrderSnapshot(request)
        );
    }

    public SyncResult syncTrade(TradeSyncRequest request) {
        return processEvent(
                request.eventId(),
                "TRADE_HISTORY_SYNC",
                "TRADE",
                String.valueOf(request.tradeId()),
                () -> {
                    upsertTradeHistory(request);
                    updateOrderByTrade(request);
                }
        );
    }

    public SyncResult syncPortfolio(PortfolioSyncRequest request) {
        return processEvent(
                request.eventId(),
                "PORTFOLIO_SNAPSHOT_SYNC",
                "PORTFOLIO",
                request.memberId() + ":" + defaultContestId(request.contestId()),
                () -> upsertPortfolioSnapshot(request)
        );
    }

    private SyncResult processEvent(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            Runnable action
    ) {
        LocalDateTime now = LocalDateTime.now();
        if ("SUCCESS".equals(findProcessStatus(eventId))) {
            return new SyncResult(eventId, eventType, aggregateType, aggregateId, "SKIPPED", "Already processed event", now);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                action.run();
                upsertSyncEventLog(eventId, eventType, aggregateType, aggregateId, "SUCCESS", null, LocalDateTime.now());
            });
            return new SyncResult(eventId, eventType, aggregateType, aggregateId, "SUCCESS", "Sync completed", LocalDateTime.now());
        } catch (RuntimeException exception) {
            upsertSyncEventLog(eventId, eventType, aggregateType, aggregateId, "FAILED", truncate(exception.getMessage()), LocalDateTime.now());
            throw exception;
        }
    }

    private void upsertOrderSnapshot(OrderSyncRequest request) {
        jdbcTemplate.update("""
                insert into order_snapshot (
                    order_id,
                    member_id,
                    contest_id,
                    stock_id,
                    stock_code,
                    stock_name,
                    side,
                    order_type,
                    order_price,
                    order_quantity,
                    remaining_quantity,
                    status,
                    reject_reason,
                    ordered_at,
                    updated_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    member_id = values(member_id),
                    contest_id = values(contest_id),
                    stock_id = values(stock_id),
                    stock_code = values(stock_code),
                    stock_name = values(stock_name),
                    side = values(side),
                    order_type = values(order_type),
                    order_price = values(order_price),
                    order_quantity = values(order_quantity),
                    remaining_quantity = values(remaining_quantity),
                    status = values(status),
                    reject_reason = values(reject_reason),
                    ordered_at = values(ordered_at),
                    updated_at = values(updated_at),
                    synced_at = current_timestamp
                """,
                request.orderId(),
                request.memberId(),
                defaultContestId(request.contestId()),
                request.stockId(),
                request.stockCode(),
                request.stockName(),
                normalize(request.side()),
                normalize(request.orderType()),
                request.orderPrice(),
                request.orderQuantity(),
                request.remainingQuantity(),
                normalize(request.status()),
                request.rejectReason(),
                request.orderedAt(),
                request.updatedAt() == null ? LocalDateTime.now() : request.updatedAt()
        );
    }

    private void upsertTradeHistory(TradeSyncRequest request) {
        jdbcTemplate.update("""
                insert into trade_history (
                    trade_id,
                    order_id,
                    member_id,
                    contest_id,
                    stock_id,
                    stock_code,
                    stock_name,
                    side,
                    executed_price,
                    executed_quantity,
                    executed_amount,
                    executed_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    order_id = values(order_id),
                    member_id = values(member_id),
                    contest_id = values(contest_id),
                    stock_id = values(stock_id),
                    stock_code = values(stock_code),
                    stock_name = values(stock_name),
                    side = values(side),
                    executed_price = values(executed_price),
                    executed_quantity = values(executed_quantity),
                    executed_amount = values(executed_amount),
                    executed_at = values(executed_at),
                    synced_at = current_timestamp
                """,
                request.tradeId(),
                request.orderId(),
                request.memberId(),
                defaultContestId(request.contestId()),
                request.stockId(),
                request.stockCode(),
                request.stockName(),
                normalize(request.side()),
                request.executedPrice(),
                request.executedQuantity(),
                request.executedAmount(),
                request.executedAt()
        );
    }

    private void updateOrderByTrade(TradeSyncRequest request) {
        jdbcTemplate.update("""
                update order_snapshot
                set remaining_quantity = greatest(remaining_quantity - ?, 0),
                    status = case
                        when greatest(remaining_quantity - ?, 0) = 0 then 'FILLED'
                        else 'PARTIALLY_FILLED'
                    end,
                    updated_at = ?,
                    synced_at = current_timestamp
                where order_id = ?
                  and status not in ('CANCELED', 'REJECTED')
                """,
                request.executedQuantity(),
                request.executedQuantity(),
                request.executedAt(),
                request.orderId()
        );
    }

    private void upsertPortfolioSnapshot(PortfolioSyncRequest request) {
        jdbcTemplate.update("""
                insert into portfolio_snapshot (
                    member_id,
                    contest_id,
                    cash_balance,
                    available_cash,
                    stock_evaluation_amount,
                    total_asset,
                    total_buy_amount,
                    total_sell_amount,
                    profit_amount,
                    profit_rate,
                    holdings_json,
                    portfolio_version,
                    onprem_updated_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    cash_balance = values(cash_balance),
                    available_cash = values(available_cash),
                    stock_evaluation_amount = values(stock_evaluation_amount),
                    total_asset = values(total_asset),
                    total_buy_amount = values(total_buy_amount),
                    total_sell_amount = values(total_sell_amount),
                    profit_amount = values(profit_amount),
                    profit_rate = values(profit_rate),
                    holdings_json = values(holdings_json),
                    portfolio_version = values(portfolio_version),
                    onprem_updated_at = values(onprem_updated_at),
                    synced_at = current_timestamp
                """,
                request.memberId(),
                defaultContestId(request.contestId()),
                request.cashBalance(),
                request.availableCash(),
                request.stockEvaluationAmount(),
                request.totalAsset(),
                request.totalBuyAmount(),
                request.totalSellAmount(),
                request.profitAmount(),
                request.profitRate(),
                request.holdingsJson(),
                request.portfolioVersion(),
                request.onpremUpdatedAt()
        );
    }

    private String findProcessStatus(String eventId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select process_status
                    from sync_event_log
                    where event_id = ?
                    """, String.class, eventId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void upsertSyncEventLog(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String processStatus,
            String failureReason,
            LocalDateTime processedAt
    ) {
        jdbcTemplate.update("""
                insert into sync_event_log (
                    event_id,
                    event_type,
                    aggregate_type,
                    aggregate_id,
                    process_status,
                    failure_reason,
                    received_at,
                    processed_at
                )
                values (?, ?, ?, ?, ?, ?, current_timestamp, ?)
                on duplicate key update
                    event_type = values(event_type),
                    aggregate_type = values(aggregate_type),
                    aggregate_id = values(aggregate_id),
                    process_status = values(process_status),
                    failure_reason = values(failure_reason),
                    processed_at = values(processed_at)
                """,
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                processStatus,
                failureReason,
                processedAt
        );
    }

    private long defaultContestId(Long contestId) {
        return contestId == null ? 0L : contestId;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }
}
