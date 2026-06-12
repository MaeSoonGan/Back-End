package com.mock.maesoongan.marketservice.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

/**
 * realtime-service가 Redis에 캐시한 지수(market:index:{market})를 주기적으로 읽어
 * market_index_snapshot(읽기 모델)에 적재한다.
 * - 장중 30초마다 적재(realtime 캐시 TTL과 동일) → 화면(REST 스냅샷)이 거의 실시간으로 갱신
 * - 마감 후엔 캐시가 비므로(null) 갱신을 건너뛰어 마지막(종가) 적재값이 그대로 유지됨
 */
@Component
public class MarketIndexSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexSnapshotScheduler.class);
    private static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ");

    private final JdbcTemplate jdbcTemplate;
    private final String realtimeBaseUrl;
    // JSON 파싱 전용 — 별도 빈 의존 없이 자체 인스턴스 사용
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public MarketIndexSnapshotScheduler(
            JdbcTemplate jdbcTemplate,
            @Value("${realtime.base-url:http://market-realtime-service:8087}") String realtimeBaseUrl
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeBaseUrl = realtimeBaseUrl;
    }

    // 평일 09~15시 KST, 10초마다 (realtime 갱신 주기와 동일)
    @Scheduled(cron = "0/10 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void captureIndexSnapshot() {
        for (String market : MARKETS) {
            try {
                captureOne(market);
            } catch (Exception exception) {
                log.warn("index snapshot capture failed: market={}, err={}", market, exception.getMessage());
            }
        }
    }

    private void captureOne(String market) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(realtimeBaseUrl + "/api/realtime/cache/index/" + market))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return;
        }

        JsonNode root = objectMapper.readTree(response.body());
        String valueJson = root.path("data").path("value").asText(null);
        // 캐시 비어있음(마감/만료) → 갱신 건너뜀(마지막 적재값=종가 유지)
        if (valueJson == null || valueJson.isBlank() || "null".equals(valueJson)) {
            return;
        }

        JsonNode idx = objectMapper.readTree(valueJson);
        BigDecimal value = decimal(idx, "value");
        BigDecimal change = decimal(idx, "change");
        BigDecimal changeRate = decimal(idx, "changeRate");

        jdbcTemplate.update("""
                insert into market_index_snapshot (market, index_value, change_amount, change_rate, is_cached, captured_at)
                values (?, ?, ?, ?, ?, ?)
                on duplicate key update
                    index_value = values(index_value),
                    change_amount = values(change_amount),
                    change_rate = values(change_rate),
                    is_cached = values(is_cached),
                    captured_at = values(captured_at)
                """, market, value, change, changeRate, false, LocalDateTime.now());
        log.info("index snapshot updated: market={}, value={}", market, value);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.decimalValue() : BigDecimal.ZERO;
    }
}
