package com.mock.maesoongan.marketservice.marketdata.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MarketDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public MarketDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StockPriceRow> findStockPrice(String code) {
        return queryOne("""
                select s.id,
                       s.code,
                       s.name,
                       s.market,
                       coalesce(p.current_price, 0) as current_price,
                       coalesce(p.change_amount, 0) as change_amount,
                       coalesce(p.change_rate, 0)  as change_rate,
                       coalesce(p.volume, 0)       as volume,
                       p.updated_at
                from stock s
                left join stock_price_snapshot p on p.stock_code = s.code
                where s.code = ? and s.status = 'ACTIVE'
                """, (rs, rowNum) -> new StockPriceRow(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("market"),
                rs.getBigDecimal("current_price"),
                rs.getBigDecimal("change_amount"),
                rs.getBigDecimal("change_rate"),
                rs.getLong("volume"),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        ), code);
    }

    public Optional<StockDailyPriceRow> findLatestDailyPrice(String code) {
        return queryOne("""
                select d.stock_code,
                       d.trade_date,
                       d.open_price,
                       d.high_price,
                       d.low_price,
                       d.close_price,
                       d.prev_close_price,
                       d.volume
                from stock_daily_price d
                join stock s on s.code = d.stock_code
                where d.stock_code = ? and s.status = 'ACTIVE'
                order by d.trade_date desc
                limit 1
                """, (rs, rowNum) -> new StockDailyPriceRow(
                rs.getString("stock_code"),
                toLocalDate(rs.getDate("trade_date")),
                rs.getBigDecimal("open_price"),
                rs.getBigDecimal("high_price"),
                rs.getBigDecimal("low_price"),
                rs.getBigDecimal("close_price"),
                rs.getBigDecimal("prev_close_price"),
                rs.getLong("volume")
        ), code);
    }

    public Optional<Long> findActiveStockId(String code) {
        return queryOne("""
                select id
                from stock
                where code = ? and status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getLong("id"), code);
    }

    public DailyPriceCoverage findDailyPriceCoverage(String code, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForObject("""
                select min(trade_date) as first_trade_date,
                       max(trade_date) as last_trade_date,
                       count(*) as row_count
                from stock_daily_price
                where stock_code = ?
                  and trade_date between ? and ?
                """, (rs, rowNum) -> new DailyPriceCoverage(
                toLocalDate(rs.getDate("first_trade_date")),
                toLocalDate(rs.getDate("last_trade_date")),
                rs.getInt("row_count")
        ), code, Date.valueOf(from), Date.valueOf(to));
    }

    public List<StockDailyPriceRow> findDailyPrices(String code, LocalDate from, LocalDate to) {
        return jdbcTemplate.query("""
                select d.stock_code,
                       d.trade_date,
                       d.open_price,
                       d.high_price,
                       d.low_price,
                       d.close_price,
                       d.prev_close_price,
                       d.volume
                from stock_daily_price d
                join stock s on s.code = d.stock_code
                where d.stock_code = ?
                  and d.trade_date between ? and ?
                  and s.status = 'ACTIVE'
                order by d.trade_date asc
                """, (rs, rowNum) -> new StockDailyPriceRow(
                rs.getString("stock_code"),
                toLocalDate(rs.getDate("trade_date")),
                rs.getBigDecimal("open_price"),
                rs.getBigDecimal("high_price"),
                rs.getBigDecimal("low_price"),
                rs.getBigDecimal("close_price"),
                rs.getBigDecimal("prev_close_price"),
                rs.getLong("volume")
        ), code, Date.valueOf(from), Date.valueOf(to));
    }

    public void upsertDailyPrices(List<StockDailyPriceUpsert> rows) {
        for (StockDailyPriceUpsert row : rows) {
            jdbcTemplate.update("""
                    insert into stock_daily_price
                        (stock_id, stock_code, trade_date, open_price, high_price, low_price, close_price, prev_close_price, volume, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                    on duplicate key update
                        stock_id = values(stock_id),
                        open_price = values(open_price),
                        high_price = values(high_price),
                        low_price = values(low_price),
                        close_price = values(close_price),
                        prev_close_price = values(prev_close_price),
                        volume = values(volume),
                        updated_at = current_timestamp
                    """,
                    row.stockId(),
                    row.code(),
                    Date.valueOf(row.tradeDate()),
                    row.openPrice(),
                    row.highPrice(),
                    row.lowPrice(),
                    row.closePrice(),
                    row.prevClosePrice(),
                    row.volume());
        }
    }

    public List<OrderbookLevelRow> findOrderbookLevels(String code, String side) {
        return jdbcTemplate.query("""
                select price, quantity, level_no
                from stock_orderbook_snapshot
                where stock_code = ? and side = ?
                order by level_no asc
                limit 10
                """, (rs, rowNum) -> new OrderbookLevelRow(
                rs.getBigDecimal("price"),
                rs.getLong("quantity"),
                rs.getInt("level_no")
        ), code, side);
    }

    public List<StockSearchRow> searchStocks(String keyword, String market, Long memberId) {
        String likeKeyword = "%" + keyword + "%";
        return jdbcTemplate.query("""
                select s.id,
                       s.code,
                       s.name,
                       s.market,
                       coalesce(p.current_price, 0) as current_price,
                       coalesce(p.change_rate, 0) as change_rate,
                       exists (
                           select 1
                           from watchlist w
                           where w.member_id = ? and w.stock_id = s.id
                       ) as is_watchlisted
                from stock s
                left join stock_price_snapshot p on p.stock_code = s.code
                where s.status = 'ACTIVE'
                  and s.market = ?
                  and (s.name like ? or s.code like ?)
                order by s.name asc, s.code asc
                limit 50
                """, (rs, rowNum) -> new StockSearchRow(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("market"),
                rs.getBigDecimal("current_price"),
                rs.getBigDecimal("change_rate"),
                rs.getBoolean("is_watchlisted")
        ), memberId, market, likeKeyword, likeKeyword);
    }

    public List<WatchlistStockRow> findWatchlistStocks(Long memberId, List<String> markets) {
        String placeholders = String.join(",", markets.stream().map(market -> "?").toList());
        Object[] args = new Object[markets.size() + 1];
        args[0] = memberId;
        for (int i = 0; i < markets.size(); i++) {
            args[i + 1] = markets.get(i);
        }

        // 가격은 순위 스냅샷(10초 갱신·폴백 有) 우선, 순위에 없으면 stock_price_snapshot으로 폴백
        return jdbcTemplate.query("""
                select s.code,
                       s.name,
                       s.market,
                       case when coalesce(r.price, 0) > 0 then r.price
                            else coalesce(p.current_price, 0) end as current_price,
                       case when coalesce(r.price, 0) > 0 then coalesce(r.change_amount, 0)
                            else coalesce(p.change_amount, 0) end as change_amount,
                       case when coalesce(r.price, 0) > 0 then coalesce(r.change_rate, 0)
                            else coalesce(p.change_rate, 0) end as change_rate
                from watchlist w
                join stock s on s.id = w.stock_id
                left join stock_price_snapshot p on p.stock_code = s.code
                left join market_ranking_snapshot r on r.stock_code = s.code and r.ranking_type = 'HTS_TOP_VIEW'
                where w.member_id = ?
                  and s.status = 'ACTIVE'
                  and s.market in (%s)
                order by w.created_at desc, s.code asc
                """.formatted(placeholders), (rs, rowNum) -> new WatchlistStockRow(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("market"),
                rs.getBigDecimal("current_price"),
                rs.getBigDecimal("change_amount"),
                rs.getBigDecimal("change_rate")
        ), args);
    }

    // 관심종목에 등록된 활성 종목 코드(중복 제거) — 가격 스냅샷 적재 대상
    public List<String> findWatchlistedStockCodes(int limit) {
        return jdbcTemplate.queryForList("""
                select distinct s.code
                from watchlist w
                join stock s on s.id = w.stock_id
                where s.status = 'ACTIVE'
                order by s.code
                limit ?
                """, String.class, limit);
    }

    // 종목별 현재가 스냅샷 적재(읽기 모델). watchlist/잔고 등이 이 값을 last-known으로 읽음.
    public void upsertStockPriceSnapshot(String stockCode, java.math.BigDecimal currentPrice,
                                         java.math.BigDecimal changeAmount, java.math.BigDecimal changeRate, long volume) {
        jdbcTemplate.update("""
                insert into stock_price_snapshot
                    (stock_code, current_price, change_amount, change_rate, volume, updated_at)
                values (?, ?, ?, ?, ?, now())
                on duplicate key update
                    current_price = values(current_price),
                    change_amount = values(change_amount),
                    change_rate = values(change_rate),
                    volume = values(volume),
                    updated_at = values(updated_at)
                """, stockCode, currentPrice, changeAmount, changeRate, volume);
    }

    // 점검 모드 활성 여부 (admin이 monitoring_status에 기록한 MAINTENANCE 상태)
    public boolean isMaintenanceEnabled() {
        List<Boolean> rows = jdbcTemplate.query("""
                select case when status = 'ENABLED' or is_maintenance = 1 then true else false end as enabled
                from monitoring_status
                where status_type = 'MAINTENANCE' and target_type = 'SYSTEM'
                order by coalesce(updated_at, created_at) desc, id desc
                limit 1
                """, (rs, rowNum) -> rs.getBoolean("enabled"));
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    public Optional<MarketIndexRow> findMarketIndex(String market) {
        return queryOne("""
                select market,
                       index_value,
                       change_amount,
                       change_rate,
                       is_cached,
                       captured_at
                from market_index_snapshot
                where market = ?
                """, (rs, rowNum) -> new MarketIndexRow(
                rs.getString("market"),
                rs.getBigDecimal("index_value"),
                rs.getBigDecimal("change_amount"),
                rs.getBigDecimal("change_rate"),
                rs.getBoolean("is_cached"),
                toLocalDateTime(rs.getTimestamp("captured_at"))
        ), market);
    }

    public List<MarketRankingRow> findMarketRankings(String rankingType) {
        return jdbcTemplate.query("""
                select r.rank_no,
                       r.stock_code,
                       s.name,
                       r.market,
                       r.price,
                       r.change_amount,
                       r.change_rate,
                       r.volume
                from market_ranking_snapshot r
                join stock s on s.code = r.stock_code
                where r.ranking_type = ?
                  and s.status = 'ACTIVE'
                order by r.rank_no asc
                limit 20
                """, (rs, rowNum) -> new MarketRankingRow(
                rs.getInt("rank_no"),
                rs.getString("stock_code"),
                rs.getString("name"),
                rs.getString("market"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("change_amount"),
                rs.getBigDecimal("change_rate"),
                rs.getLong("volume")
        ), rankingType);
    }

    public boolean existsActiveStock(String stockCode) {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from stock
                where code = ? and status = 'ACTIVE'
                """, Long.class, stockCode);
        return count != null && count > 0;
    }

    public boolean existsWatchlist(Long memberId, String stockCode) {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from watchlist w
                join stock s on s.id = w.stock_id
                where w.member_id = ? and s.code = ?
                """, Long.class, memberId, stockCode);
        return count != null && count > 0;
    }

    public int countWatchlist(Long memberId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from watchlist
                where member_id = ?
                """, Integer.class, memberId);
        return count == null ? 0 : count;
    }

    public int insertWatchlist(Long memberId, String stockCode) {
        return jdbcTemplate.update("""
                insert into watchlist (member_id, stock_id, created_at)
                select ?, s.id, current_timestamp
                from stock s
                where s.code = ? and s.status = 'ACTIVE'
                """, memberId, stockCode);
    }

    public int deleteWatchlist(Long memberId, String stockCode) {
        return jdbcTemplate.update("""
                delete w
                from watchlist w
                join stock s on s.id = w.stock_id
                where w.member_id = ? and s.code = ?
                """, memberId, stockCode);
    }

    private <T> Optional<T> queryOne(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record StockPriceRow(
            long stockId,
            String code,
            String name,
            String market,
            BigDecimal currentPrice,
            BigDecimal changeAmount,
            BigDecimal changeRate,
            long volume,
            LocalDateTime updatedAt
    ) {
    }

    public record StockDailyPriceRow(
            String code,
            LocalDate tradeDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal prevClosePrice,
            long volume
    ) {
    }

    public record DailyPriceCoverage(
            LocalDate firstTradeDate,
            LocalDate lastTradeDate,
            int rowCount
    ) {
    }

    public record StockDailyPriceUpsert(
            long stockId,
            String code,
            LocalDate tradeDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal prevClosePrice,
            long volume
    ) {
    }

    public record OrderbookLevelRow(
            BigDecimal price,
            long quantity,
            int levelNo
    ) {
    }

    public record StockSearchRow(
            long stockId,
            String stockCode,
            String stockName,
            String market,
            BigDecimal currentPrice,
            BigDecimal changeRate,
            boolean watchlisted
    ) {
    }

    public record WatchlistStockRow(
            String code,
            String name,
            String market,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate
    ) {
    }

    public record MarketIndexRow(
            String market,
            BigDecimal value,
            BigDecimal change,
            BigDecimal changeRate,
            boolean cached,
            LocalDateTime capturedAt
    ) {
    }

    public record MarketRankingRow(
            int rank,
            String code,
            String name,
            String market,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            long volume
    ) {
    }
}
