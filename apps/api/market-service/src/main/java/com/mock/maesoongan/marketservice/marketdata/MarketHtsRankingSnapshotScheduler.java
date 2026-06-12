package com.mock.maesoongan.marketservice.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.stock.StockChartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * realtime-service의 실시간 조회상위 순위(/api/market/ranking/hts-top-view)를 주기적으로 읽어
 * market_ranking_snapshot(ranking_type=HTS_TOP_VIEW)에 적재한다.
 * - 조회 시 stock 테이블을 조인해 종목명을 채우므로(읽기 모델), 코드 대신 종목명이 표시됨
 * - 가격이 0(보강 실패)인 종목은 마지막 종가(stock_daily_price)로 폴백해 0/빈값 노출 방지
 * - 캐시가 비면(마감/일시 실패) 적재를 건너뛰어 마지막 스냅샷이 그대로 유지됨(로딩 중 0 안 보임)
 */
@Component
public class MarketHtsRankingSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketHtsRankingSnapshotScheduler.class);
    private static final String RANKING_TYPE = "HTS_TOP_VIEW";

    // 한 주기에 백필할 최대 종목 수(REST rate 보호) + 이미 시도한 종목(재시도 방지)
    private static final int MAX_BACKFILL_PER_CYCLE = 2;

    private final JdbcTemplate jdbcTemplate;
    private final MarketDataRepository marketDataRepository;
    private final StockChartService stockChartService;
    private final String realtimeBaseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Set<String> backfillAttempted = ConcurrentHashMap.newKeySet();
    // 종목별 직전 "좋은 값"(가격+등락률 묶음) — 보강 실패 시 이 값을 유지해 0으로 깜빡이지 않게 함
    private final Map<String, Quote> lastGood = new ConcurrentHashMap<>();

    private record Quote(BigDecimal price, BigDecimal change, BigDecimal changeRate, long volume) {
    }

    public MarketHtsRankingSnapshotScheduler(
            JdbcTemplate jdbcTemplate,
            MarketDataRepository marketDataRepository,
            StockChartService stockChartService,
            @Value("${realtime.base-url:http://market-realtime-service:8087}") String realtimeBaseUrl
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.marketDataRepository = marketDataRepository;
        this.stockChartService = stockChartService;
        this.realtimeBaseUrl = realtimeBaseUrl;
    }

    // 평일 09~15시 KST, 10초마다 (realtime 갱신 주기와 동일 — 그 이하는 같은 값 재저장이라 무의미)
    @Scheduled(cron = "0/10 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void captureRankingSnapshot() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(realtimeBaseUrl + "/api/market/ranking/hts-top-view"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }

            JsonNode items = objectMapper.readTree(response.body()).path("items");
            if (!items.isArray() || items.isEmpty()) {
                // 캐시 비었음(마감/일시 실패) → 마지막 스냅샷 유지
                return;
            }

            int rankNo = 0;
            List<String> zeroPriceCodes = new ArrayList<>();
            for (JsonNode item : items) {
                rankNo++;
                String stockCode = item.path("stockCode").asText(null);
                if (stockCode == null || stockCode.isBlank()) {
                    continue;
                }

                BigDecimal currentPrice = decimal(item, "currentPrice");
                Quote quote;
                if (currentPrice.signum() > 0) {
                    // 보강 성공 → 신선한 값 사용 + 직전값 갱신
                    quote = new Quote(currentPrice, decimal(item, "changePrice"), decimal(item, "changeRate"), item.path("volume").asLong(0));
                    lastGood.put(stockCode, quote);
                } else {
                    Quote prev = lastGood.get(stockCode);
                    if (prev != null) {
                        // 보강 실패 → 직전 좋은 값(가격+등락률 묶음) 유지 → 0으로 깜빡이지 않음
                        quote = prev;
                    } else {
                        // 직전값도 없음 → 종가 폴백(가격만, 등락률 0)
                        BigDecimal close = marketDataRepository.findLatestDailyPrice(stockCode)
                                .map(row -> row.closePrice() == null ? BigDecimal.ZERO : row.closePrice())
                                .orElse(BigDecimal.ZERO);
                        quote = new Quote(close, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
                        if (close.signum() <= 0) {
                            zeroPriceCodes.add(stockCode); // 현재가·종가·직전값 모두 없음 → 백필 후보
                        }
                    }
                }
                upsert(rankNo, stockCode, quote.price(), quote.change(), quote.changeRate(), quote.volume());
            }

            // 현재 순위 개수보다 큰 잔여 행 제거
            jdbcTemplate.update(
                    "delete from market_ranking_snapshot where ranking_type = ? and rank_no > ?",
                    RANKING_TYPE, rankNo
            );
            log.info("hts ranking snapshot updated: count={}", rankNo);

            backfillMissingPrices(zeroPriceCodes);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.warn("hts ranking snapshot capture failed: {}", exception.getMessage());
        }
    }

    // 현재가/종가가 모두 없는 종목의 일별시세를 백필(REST). 주기당 최대 N개 + 이미 시도한 종목은 스킵(rate 보호).
    private void backfillMissingPrices(List<String> zeroPriceCodes) {
        int done = 0;
        for (String code : zeroPriceCodes) {
            if (done >= MAX_BACKFILL_PER_CYCLE) {
                break;
            }
            if (!backfillAttempted.add(code)) {
                continue; // 이미 시도함
            }
            try {
                stockChartService.ensureRecentDailyPrices(code);
                done++;
                log.info("hts ranking backfill daily prices: code={}", code);
            } catch (RuntimeException exception) {
                log.warn("hts ranking backfill failed: code={}, err={}", code, exception.getMessage());
            }
        }
    }

    private void upsert(int rankNo, String stockCode, BigDecimal price, BigDecimal change, BigDecimal changeRate, long volume) {
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
                """, RANKING_TYPE, rankNo, stockCode, "", price, change, changeRate, volume, java.math.BigDecimal.ZERO, LocalDateTime.now());
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.decimalValue() : BigDecimal.ZERO;
    }
}
