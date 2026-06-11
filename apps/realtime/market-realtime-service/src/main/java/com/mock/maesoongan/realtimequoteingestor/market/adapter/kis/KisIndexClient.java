package com.mock.maesoongan.realtimequoteingestor.market.adapter.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisAccessTokenClient;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisProperties;
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
import java.util.Locale;
import java.util.Optional;

/**
 * KIS 국내업종 현재지수 조회 REST(/uapi/domestic-stock/v1/quotations/inquire-index-price) 클라이언트.
 * ws 구독과 달리 요청-응답형이라 실시간 등록 한도를 쓰지 않는다. 코스피/코스닥 지수를 주기 조회할 때 사용한다.
 */
@Component
public class KisIndexClient {

    private static final Logger log = LoggerFactory.getLogger(KisIndexClient.class);

    private final KisProperties properties;
    private final KisAccessTokenClient accessTokenClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KisIndexClient(
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

    // 실패 시 예외를 던지지 않고 Optional.empty() 반환.
    public Optional<MarketIndexResponse> fetchIndex(String market) {
        String normalizedMarket = normalizeMarket(market);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(normalizedMarket))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessTokenClient.getAccessToken())
                    .header("appkey", properties.appKey())
                    .header("appsecret", properties.appSecret())
                    .header("tr_id", properties.indexQuoteTrId())
                    .header("custtype", properties.customerType())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("KIS index quote failed. market={}, status={}", normalizedMarket, response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String resultCode = root.path("rt_cd").asText();
            if (StringUtils.hasText(resultCode) && !"0".equals(resultCode)) {
                log.warn("KIS index quote rt_cd={}, market={}, msg={}", resultCode, normalizedMarket, root.path("msg1").asText());
                return Optional.empty();
            }

            JsonNode output = root.path("output");
            if (output.isMissingNode() || output.isNull()) {
                return Optional.empty();
            }

            String sign = text(output, "prdy_vrss_sign");
            return Optional.of(new MarketIndexResponse(
                    normalizedMarket,
                    decimal(output, "bstp_nmix_prpr"),
                    signedDecimal(output, sign, "bstp_nmix_prdy_vrss"),
                    signedDecimal(output, sign, "bstp_nmix_prdy_ctrt"),
                    longValue(output, "acml_vol"),
                    LocalDateTime.now()
            ));
        } catch (java.io.IOException exception) {
            log.warn("KIS index quote request failed. market={}, err={}", normalizedMarket, exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private URI uri(String market) {
        String baseUrl = trimTrailingSlash(properties.baseUrl());
        String path = properties.indexQuotePath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(baseUrl + path + "?FID_COND_MRKT_DIV_CODE=U&FID_INPUT_ISCD=" + indexCode(market));
    }

    private String normalizeMarket(String market) {
        return market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
    }

    private String indexCode(String market) {
        return switch (market) {
            case "KOSPI" -> "0001";
            case "KOSDAQ" -> "1001";
            default -> market;
        };
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

    private BigDecimal signedDecimal(JsonNode node, String sign, String field) {
        BigDecimal value = decimal(node, field);
        if (value.signum() < 0) {
            return value;
        }
        if ("4".equals(sign) || "5".equals(sign) || "-".equals(sign)) {
            return value.negate();
        }
        return value;
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
}
