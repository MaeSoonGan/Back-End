package com.mock.maesoongan.orderservice.portfolio;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PortfolioRepository {

    private final JdbcTemplate jdbcTemplate;

    public PortfolioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PortfolioRow> findPortfolio(long memberId, long contestId) {
        return queryOne("""
                select member_id, contest_id, cash_balance, available_cash, stock_evaluation_amount,
                       total_asset, profit_amount, profit_rate, holdings_json, portfolio_version, synced_at
                from portfolio_snapshot
                where member_id = ? and contest_id = ?
                """, (rs, rowNum) -> new PortfolioRow(
                rs.getLong("member_id"),
                rs.getLong("contest_id"),
                rs.getBigDecimal("cash_balance"),
                rs.getBigDecimal("available_cash"),
                rs.getBigDecimal("stock_evaluation_amount"),
                rs.getBigDecimal("total_asset"),
                rs.getBigDecimal("profit_amount"),
                rs.getBigDecimal("profit_rate"),
                rs.getString("holdings_json"),
                rs.getLong("portfolio_version"),
                toLocalDateTime(rs.getTimestamp("synced_at"))
        ), memberId, contestId);
    }

    public Optional<StockPriceRow> findStockPrice(String stockCode) {
        return queryOne("""
                select s.code,
                       s.name,
                       coalesce(p.current_price, 0) as current_price
                from stock s
                left join stock_price_snapshot p on p.stock_code = s.code
                where s.code = ? and s.status = 'ACTIVE'
                """, (rs, rowNum) -> new StockPriceRow(
                rs.getString("code"),
                rs.getString("name"),
                rs.getBigDecimal("current_price")
        ), stockCode);
    }

    public long countPendingSellQuantity(long memberId, long contestId, String stockCode) {
        Long quantity = jdbcTemplate.queryForObject("""
                select coalesce(sum(remaining_quantity), 0)
                from order_snapshot
                where member_id = ?
                  and contest_id = ?
                  and stock_code = ?
                  and side = 'SELL'
                  and status in ('PENDING', 'OPEN', 'PARTIAL', 'CANCEL_REQUESTED')
                """, Long.class, memberId, contestId, stockCode);
        return quantity == null ? 0 : quantity;
    }

    public Optional<ContestAccountRow> findContestAccount(long memberId, long contestId) {
        return queryOne("""
                select c.id as contest_id,
                       c.seed_money,
                       p.cash_balance,
                       p.stock_evaluation_amount,
                       p.total_asset,
                       p.profit_amount,
                       p.profit_rate,
                       coalesce(r.rank_no, 0) as rank_no,
                       p.available_cash,
                       (
                           select count(*)
                           from contest_participation cp
                           where cp.contest_id = c.id and cp.status = 'ACTIVE'
                       ) as total_participants
                from contest c
                join contest_participation cp on cp.contest_id = c.id and cp.member_id = ? and cp.status = 'ACTIVE'
                left join portfolio_snapshot p on p.member_id = cp.member_id and p.contest_id = c.id
                left join ranking r on r.member_id = cp.member_id and r.contest_id = c.id
                where c.id = ?
                """, (rs, rowNum) -> new ContestAccountRow(
                rs.getLong("contest_id"),
                rs.getBigDecimal("seed_money"),
                rs.getBigDecimal("cash_balance"),
                rs.getBigDecimal("stock_evaluation_amount"),
                rs.getBigDecimal("total_asset"),
                rs.getBigDecimal("profit_amount"),
                rs.getBigDecimal("profit_rate"),
                rs.getInt("rank_no"),
                rs.getLong("total_participants"),
                rs.getBigDecimal("available_cash")
        ), memberId, contestId);
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

    public record PortfolioRow(
            long memberId,
            long contestId,
            BigDecimal cashBalance,
            BigDecimal availableCash,
            BigDecimal stockEvaluationAmount,
            BigDecimal totalAsset,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            String holdingsJson,
            long portfolioVersion,
            LocalDateTime syncedAt
    ) {
    }

    public record StockPriceRow(String stockCode, String stockName, BigDecimal currentPrice) {
    }

    public record ContestAccountRow(
            long contestId,
            BigDecimal seedMoney,
            BigDecimal cashBalance,
            BigDecimal stockEvaluationAmount,
            BigDecimal currentAsset,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            int rank,
            long totalParticipants,
            BigDecimal availableBalance
    ) {
    }
}
