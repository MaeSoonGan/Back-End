package com.mock.maesoongan.marketservice.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * realtime-service가 Redis에 캐시한 종목별 현재가(stock:{code}:price)를 주기적으로 읽어
 * 거래대금(tradingValue) 내림차순으로 정렬해 market_ranking_snapshot(읽기 모델, ranking_type=TRADING_VALUE)에 적재한다.
 * - 전체 종목 거래대금은 받을 수 없으므로, realtime pod가 구독(ingestion)하는 ranking.stock-codes 집합 내에서만 순위를 매긴다.
 * - 장중 5분마다 적재 → 마감 후 화면(REST 스냅샷)에 그날 값이 반영
 * - 마감/만료로 캐시가 모두 비면 갱신을 건너뛰어 마지막 적재값(종가)이 유지됨
 */
@Component
public class MarketRankingSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketRankingSnapshotScheduler.class);
    private static final String RANKING_TYPE = "TRADING_VALUE";

    private final JdbcTemplate jdbcTemplate;
    private final String realtimeBaseUrl;
    private final List<String> stockCodes;
    // JSON 파싱 전용 — 별도 빈 의존 없이 자체 인스턴스 사용
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public MarketRankingSnapshotScheduler(
            JdbcTemplate jdbcTemplate,
            @Value("${realtime.base-url:http://market-realtime-service:8087}") String realtimeBaseUrl,
            @Value("${ranking.stock-codes:005930,000660,035420,005380,035720}") List<String> stockCodes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeBaseUrl = realtimeBaseUrl;
        this.stockCodes = stockCodes;
    }

    // 평일 09:00~15:55 KST, 5분마다 (장중 주기 + 마감 시점 캡처)
    @Scheduled(cron = "0 0/5 9-15 * * MON-FRI", zone = "Asia/Seoul")
    @Transactional
    public void captureRankingSnapshot() {
        List<RankingRow> rows = new ArrayList<>();
        for (String code : stockCodes) {
            try {
                RankingRow row = fetchOne(code.trim());
                if (row != null) {
                    rows.add(row);
                }
            } catch (Exception exception) {
                log.warn("ranking snapshot fetch failed: code={}, err={}", code, exception.getMessage());
            }
        }
        // 캐시에 데이터가 하나도 없으면(마감/만료) 마지막 적재값 유지
        if (rows.isEmpty()) {
            return;
        }

        // 거래대금 내림차순 → rank_no 부여
        rows.sort(Comparator.comparing((RankingRow r) -> r.tradingValue).reversed());

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < rows.size(); i++) {
            RankingRow row = rows.get(i);
            int rankNo = i + 1;
            jdbcTemplate.update("""
                    insert into market_ranking_snapshot
                        (ranking_type, rank_no, stock_code, market, price, change_amount, change_rate, volume, trading_amount, captured_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update
                        stock_code = values(stock_code),
                        market = values(market),
                        price = values(price),
                        change_amount = values(change_amount),
                        change_rate = values(change_rate),
                        volume = values(volume),
                        trading_amount = values(trading_amount),
                        captured_at = values(captured_at)
                    """,
                    RANKING_TYPE, rankNo, row.stockCode, row.market, row.price,
                    row.changeAmount, row.changeRate, row.volume, row.tradingValue, now);
        }
        // 채운 순위(rows.size())보다 큰 rank_no 잔여 행 제거(추적 종목 수가 줄어든 경우)
        jdbcTemplate.update(
                "delete from market_ranking_snapshot where ranking_type = ? and rank_no > ?",
                RANKING_TYPE, rows.size());
        log.info("ranking snapshot updated: type={}, count={}", RANKING_TYPE, rows.size());
    }

    private RankingRow fetchOne(String code) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(realtimeBaseUrl + "/api/realtime/cache/price/" + code))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        JsonNode root = objectMapper.readTree(response.body());
        String valueJson = root.path("data").path("value").asText(null);
        // 캐시 비어있음(미구독/만료) → 이 종목은 건너뜀
        if (valueJson == null || valueJson.isBlank() || "null".equals(valueJson)) {
            return null;
        }

        JsonNode price = objectMapper.readTree(valueJson);
        String market = findMarket(code);
        // 상장 종목이 아니면(또는 비활성) 스냅샷에서 제외
        if (market == null) {
            return null;
        }

        RankingRow row = new RankingRow();
        row.stockCode = code;
        row.market = market;
        row.price = decimal(price, "currentPrice");
        row.changeAmount = decimal(price, "changePrice");
        row.changeRate = decimal(price, "changeRate");
        row.volume = price.path("volume").asLong(0);
        row.tradingValue = decimal(price, "tradingValue");
        return row;
    }

    private String findMarket(String code) {
        List<String> markets = jdbcTemplate.query(
                "select market from stock where code = ? and status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("market"), code);
        return markets.isEmpty() ? null : markets.get(0);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.decimalValue() : BigDecimal.ZERO;
    }

    private static class RankingRow {
        String stockCode;
        String market;
        BigDecimal price;
        BigDecimal changeAmount;
        BigDecimal changeRate;
        long volume;
        BigDecimal tradingValue;
    }
}
