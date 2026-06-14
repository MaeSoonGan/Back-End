package com.mock.maesoongan.realtimequoteingestor.market.adapter.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisAccessTokenClient;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisProperties;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class KisOrderbookClient {

    private static final Logger log = LoggerFactory.getLogger(KisOrderbookClient.class);

    private final KisProperties properties;
    private final KisAccessTokenClient accessTokenClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");

    public KisOrderbookClient(
            KisProperties properties,
            KisAccessTokenClient accessTokenClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.accessTokenClient = accessTokenClient;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<KisOrderbookSnapshot> fetchOrderbook(String stockCode) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(stockCode))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessTokenClient.getAccessToken())
                    .header("appkey", properties.appKey())
                    .header("appsecret", properties.appSecret())
                    .header("tr_id", properties.inquireOrderbookTrId())
                    .header("custtype", properties.customerType())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("KIS inquire-orderbook failed. code={}, status={}", stockCode, response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String resultCode = root.path("rt_cd").asText();
            if (StringUtils.hasText(resultCode) && !"0".equals(resultCode)) {
                log.warn("KIS inquire-orderbook rt_cd={}, code={}, msg={}", resultCode, stockCode, root.path("msg1").asText());
                return Optional.empty();
            }

            return parseSnapshot(stockCode, root);
        } catch (java.io.IOException exception) {
            log.warn("KIS inquire-orderbook request failed. code={}, err={}", stockCode, exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("KIS inquire-orderbook request failed. code={}, err={}", stockCode, exception.getMessage());
            return Optional.empty();
        }
    }

    Optional<KisOrderbookSnapshot> parseSnapshot(String stockCode, JsonNode root) {
        JsonNode output = orderbookOutput(root);
        if (output.isMissingNode() || output.isNull()) {
            return Optional.empty();
        }

        List<OrderbookLevel> asks = levels(output, "askp", "askp_rsqn");
        List<OrderbookLevel> bids = levels(output, "bidp", "bidp_rsqn");
        if (asks.isEmpty() || bids.isEmpty()) {
            log.warn("KIS inquire-orderbook has no levels. code={}, outputType={}", stockCode, output.getNodeType());
            return Optional.empty();
        }

        return Optional.of(new KisOrderbookSnapshot(
                text(output, "hts_kor_isnm"),
                asks,
                bids,
                LocalDateTime.now(zoneId).withNano(0)
        ));
    }

    private URI uri(String stockCode) {
        String baseUrl = trimTrailingSlash(properties.baseUrl());
        String path = properties.inquireOrderbookPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(baseUrl + path + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + stockCode);
    }

    private JsonNode orderbookOutput(JsonNode root) {
        JsonNode output1 = firstObject(root.path("output1"));
        if (!output1.isMissingNode() && !output1.isNull()) {
            return output1;
        }
        return firstObject(root.path("output"));
    }

    private JsonNode firstObject(JsonNode node) {
        if (!node.isArray()) {
            return node;
        }
        if (node.isEmpty()) {
            return node.path(0);
        }
        return node.get(0);
    }

    private List<OrderbookLevel> levels(JsonNode output, String pricePrefix, String quantityPrefix) {
        List<OrderbookLevel> levels = new ArrayList<>();
        for (int level = 1; level <= 10; level++) {
            BigDecimal price = decimal(output, pricePrefix + level);
            long quantity = longValue(output, quantityPrefix + level);
            if (price.signum() > 0 || quantity > 0) {
                levels.add(new OrderbookLevel(price, quantity));
            }
        }
        return levels;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        return value == null ? "" : value.trim();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(cleanNumber(value));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private long longValue(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(cleanNumber(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String cleanNumber(String value) {
        return value.replace(",", "").replace("+", "").trim();
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("kis.base-url is required");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record KisOrderbookSnapshot(
            String stockName,
            List<OrderbookLevel> asks,
            List<OrderbookLevel> bids,
            LocalDateTime timestamp
    ) {
    }
}
