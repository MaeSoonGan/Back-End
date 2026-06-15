package com.mock.maesoongan.orderservice.order;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StockRow> findActiveStock(long stockId, String stockCode) {
        return queryOne("""
                select s.id,
                       s.code,
                       s.name,
                       coalesce(p.current_price, 0) as current_price
                from stock s
                left join stock_price_snapshot p on p.stock_code = s.code
                where s.id = ? and s.code = ? and s.status = 'ACTIVE'
                """, (rs, rowNum) -> new StockRow(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBigDecimal("current_price")
        ), stockId, stockCode);
    }

    public boolean existsActiveContestParticipation(long memberId, long contestId) {
        if (contestId == 0) {
            return true;
        }
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from contest_participation cp
                join contest c on c.id = cp.contest_id
                where cp.member_id = ?
                  and cp.contest_id = ?
                  and cp.status = 'ACTIVE'
                  and c.status in ('ACTIVE', 'CLOSING_SOON')
                """, Long.class, memberId, contestId);
        return count != null && count > 0;
    }

    public Optional<PortfolioRow> findPortfolio(long memberId, long contestId) {
        return queryOne("""
                select member_id,
                       contest_id,
                       available_cash,
                       portfolio_version
                from portfolio_snapshot
                where member_id = ? and contest_id = ?
                """, (rs, rowNum) -> new PortfolioRow(
                rs.getLong("member_id"),
                rs.getLong("contest_id"),
                rs.getBigDecimal("available_cash"),
                rs.getLong("portfolio_version")
        ), memberId, contestId);
    }

    public Optional<Long> findAccountId(long memberId, long contestId) {
        return queryOne("""
                select account_id
                from portfolio_snapshot
                where member_id = ? and contest_id = ?
                """, (rs, rowNum) -> {
            long accountId = rs.getLong("account_id");
            return rs.wasNull() ? null : accountId;
        }, memberId, contestId);
    }

    public long nextOrderId() {
        Long next = jdbcTemplate.queryForObject("""
                select coalesce(max(order_id), 0) + 1
                from order_snapshot
                """, Long.class);
        return next == null ? 1L : next;
    }

    public void insertOrder(OrderInsertCommand command) {
        jdbcTemplate.update("""
                insert into order_snapshot
                (order_id, member_id, contest_id, stock_id, stock_code, stock_name, side, order_type,
                 order_price, order_quantity, remaining_quantity, status, reject_reason, ordered_at,
                 updated_at, synced_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', null, ?, null, ?)
                """,
                command.orderId(),
                command.memberId(),
                command.contestId(),
                command.stockId(),
                command.stockCode(),
                command.stockName(),
                command.side(),
                command.orderType(),
                command.orderPrice(),
                command.orderQuantity(),
                command.orderQuantity(),
                command.orderedAt(),
                command.orderedAt()
        );
    }

    public int markCancelRequested(long memberId, long orderId, LocalDateTime requestedAt) {
        return jdbcTemplate.update("""
                update order_snapshot
                set status = 'CANCEL_REQUESTED',
                    updated_at = ?,
                    synced_at = ?
                where member_id = ?
                  and order_id = ?
                  and status in ('PENDING', 'OPEN', 'PARTIAL', 'PARTIALLY_FILLED')
                  and remaining_quantity > 0
                """, requestedAt, requestedAt, memberId, orderId);
    }

    public Optional<OrderRow> findOrder(long memberId, long orderId) {
        return queryOne("""
                select order_id, member_id, contest_id, stock_id, stock_code, stock_name, side, order_type,
                       order_price, order_quantity, remaining_quantity, status, reject_reason, ordered_at, updated_at
                from order_snapshot
                where member_id = ? and order_id = ?
                """, this::toOrderRow, memberId, orderId);
    }

    public List<OrderRow> findOrders(long memberId, Long contestId, String status, LocalDate date, int limit, int offset) {
        return jdbcTemplate.query("""
                select order_id, member_id, contest_id, stock_id, stock_code, stock_name, side, order_type,
                       order_price, order_quantity, remaining_quantity, status, reject_reason, ordered_at, updated_at
                from order_snapshot
                where member_id = ?
                  and (? is null or contest_id = ?)
                  and (? = 'ALL' or status = ?)
                  and ordered_at >= ? and ordered_at < ?
                order by ordered_at desc, order_id desc
                limit ?
                offset ?
                """, this::toOrderRow, memberId, contestId, contestId, status, status,
                date.atStartOfDay(), date.plusDays(1).atStartOfDay(), limit, offset);
    }

    public int countOrders(long memberId, Long contestId, String status, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from order_snapshot
                where member_id = ?
                  and (? is null or contest_id = ?)
                  and (? = 'ALL' or status = ?)
                  and ordered_at >= ? and ordered_at < ?
                """, Integer.class, memberId, contestId, contestId, status, status,
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        return count == null ? 0 : count;
    }

    public List<TradeRow> findTrades(long memberId, Long contestId, LocalDate from, LocalDate to, String side, int limit, int offset) {
        return jdbcTemplate.query("""
                select trade_id, order_id, member_id, contest_id, stock_id, stock_code, stock_name, side,
                       executed_price, executed_quantity, executed_amount, executed_at
                from trade_history
                where member_id = ?
                  and (? is null or contest_id = ?)
                  and (? = 'ALL' or side = ?)
                  and executed_at >= ? and executed_at < ?
                order by executed_at desc, trade_id desc
                limit ?
                offset ?
                """, (rs, rowNum) -> new TradeRow(
                rs.getLong("trade_id"),
                rs.getLong("order_id"),
                rs.getLong("member_id"),
                rs.getLong("contest_id"),
                rs.getLong("stock_id"),
                rs.getString("stock_code"),
                rs.getString("stock_name"),
                rs.getString("side"),
                rs.getBigDecimal("executed_price"),
                rs.getLong("executed_quantity"),
                rs.getBigDecimal("executed_amount"),
                toLocalDateTime(rs.getTimestamp("executed_at"))
        ), memberId, contestId, contestId, side, side, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), limit, offset);
    }

    public int countTrades(long memberId, Long contestId, LocalDate from, LocalDate to, String side) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from trade_history
                where member_id = ?
                  and (? is null or contest_id = ?)
                  and (? = 'ALL' or side = ?)
                  and executed_at >= ? and executed_at < ?
                """, Integer.class, memberId, contestId, contestId, side, side, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return count == null ? 0 : count;
    }

    public TradeSummaryRow summarizeTrades(long memberId, Long contestId, LocalDate from, LocalDate to, String side) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when side = 'BUY' then executed_quantity else 0 end), 0) as buy_quantity,
                       coalesce(sum(case when side = 'SELL' then executed_quantity else 0 end), 0) as sell_quantity,
                       coalesce(sum(executed_amount), 0) as total_amount
                from trade_history
                where member_id = ?
                  and (? is null or contest_id = ?)
                  and (? = 'ALL' or side = ?)
                  and executed_at >= ? and executed_at < ?
                """, (rs, rowNum) -> new TradeSummaryRow(
                rs.getLong("buy_quantity"),
                rs.getLong("sell_quantity"),
                rs.getBigDecimal("total_amount")
        ), memberId, contestId, contestId, side, side, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private OrderRow toOrderRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OrderRow(
                rs.getLong("order_id"),
                rs.getLong("member_id"),
                rs.getLong("contest_id"),
                rs.getLong("stock_id"),
                rs.getString("stock_code"),
                rs.getString("stock_name"),
                rs.getString("side"),
                rs.getString("order_type"),
                rs.getBigDecimal("order_price"),
                rs.getLong("order_quantity"),
                rs.getLong("remaining_quantity"),
                rs.getString("status"),
                rs.getString("reject_reason"),
                toLocalDateTime(rs.getTimestamp("ordered_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private <T> Optional<T> queryOne(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record StockRow(long stockId, String stockCode, String stockName, BigDecimal currentPrice) {
    }

    public record PortfolioRow(long memberId, long contestId, BigDecimal availableCash, long portfolioVersion) {
    }

    public record OrderInsertCommand(
            long orderId,
            long memberId,
            long contestId,
            long stockId,
            String stockCode,
            String stockName,
            String side,
            String orderType,
            BigDecimal orderPrice,
            long orderQuantity,
            LocalDateTime orderedAt
    ) {
    }

    public record OrderRow(
            long orderId,
            long memberId,
            long contestId,
            long stockId,
            String stockCode,
            String stockName,
            String side,
            String orderType,
            BigDecimal orderPrice,
            long orderQuantity,
            long remainingQuantity,
            String status,
            String rejectReason,
            LocalDateTime orderedAt,
            LocalDateTime updatedAt
    ) {
    }

    public record TradeRow(
            long tradeId,
            long orderId,
            long memberId,
            long contestId,
            long stockId,
            String stockCode,
            String stockName,
            String side,
            BigDecimal executedPrice,
            long executedQuantity,
            BigDecimal executedAmount,
            LocalDateTime executedAt
    ) {
    }

    public record TradeSummaryRow(long buyQuantity, long sellQuantity, BigDecimal totalAmount) {
    }
}
