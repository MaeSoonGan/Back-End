package com.mock.maesoongan.orderservice.portfolio;

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

    // 보유종목 평균 체결가(매수 가중평균) = Σ(체결금액) / Σ(체결수량), BUY 한정
    public Optional<BigDecimal> findAverageBuyPrice(long memberId, long contestId, String stockCode) {
        return queryOne("""
                select sum(executed_amount) / nullif(sum(executed_quantity), 0) as avg_price
                from trade_history
                where member_id = ?
                  and contest_id = ?
                  and stock_code = ?
                  and side = 'BUY'
                """, (rs, rowNum) -> rs.getBigDecimal("avg_price"), memberId, contestId, stockCode);
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

    public Optional<BigDecimal> findContestSeedMoney(long contestId) {
        return queryOne("""
                select seed_money
                from contest
                where id = ?
                """, (rs, rowNum) -> rs.getBigDecimal("seed_money"), contestId);
    }

    public int cancelOpenOrdersForReset(long memberId, long contestId, LocalDateTime canceledAt) {
        return jdbcTemplate.update("""
                update order_snapshot
                set status = 'CANCELED',
                    reject_reason = 'SEED_MONEY_RESET',
                    updated_at = ?,
                    synced_at = ?
                where member_id = ?
                  and contest_id = ?
                  and status in ('PENDING', 'OPEN', 'PARTIAL', 'CANCEL_REQUESTED')
                """, canceledAt, canceledAt, memberId, contestId);
    }

    public void resetPortfolio(long memberId, long contestId, BigDecimal seedMoney, LocalDateTime resetAt) {
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
                values (?, ?, ?, ?, 0, ?, 0, 0, 0, 0, '[]', 1, ?, ?)
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
                    portfolio_version = portfolio_version + 1,
                    onprem_updated_at = values(onprem_updated_at),
                    synced_at = values(synced_at)
                """,
                memberId,
                contestId,
                seedMoney,
                seedMoney,
                seedMoney,
                resetAt,
                resetAt
        );
    }

    public List<DailyProfitRow> findDailyProfitHistory(long memberId, long contestId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query("""
                select snapshot_date, profit_rate, total_asset
                from portfolio_daily_snapshot
                where member_id = ?
                  and contest_id = ?
                  and snapshot_date between ? and ?
                order by snapshot_date
                """, (rs, rowNum) -> new DailyProfitRow(
                rs.getDate("snapshot_date").toLocalDate(),
                rs.getBigDecimal("profit_rate"),
                rs.getBigDecimal("total_asset")
        ), memberId, contestId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
    }

    public int snapshotDaily(LocalDate snapshotDate) {
        return jdbcTemplate.update("""
                insert into portfolio_daily_snapshot
                    (member_id, contest_id, snapshot_date, profit_rate, profit_amount, total_asset)
                select member_id, contest_id, ?, profit_rate, profit_amount, total_asset
                from portfolio_snapshot
                on duplicate key update
                    profit_rate   = values(profit_rate),
                    profit_amount = values(profit_amount),
                    total_asset   = values(total_asset)
                """, java.sql.Date.valueOf(snapshotDate));
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

    public record DailyProfitRow(LocalDate snapshotDate, BigDecimal profitRate, BigDecimal totalAsset) {
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
