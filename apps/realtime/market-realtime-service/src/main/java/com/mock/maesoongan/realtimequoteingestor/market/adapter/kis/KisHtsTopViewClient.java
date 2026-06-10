package com.mock.maesoongan.realtimequoteingestor.market.adapter.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketRankingItemResponse;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisAccessTokenClient;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class KisHtsTopViewClient {

    private final KisProperties properties;
    private final KisAccessTokenClient accessTokenClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KisHtsTopViewClient(
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

    public List<MarketRankingItemResponse> fetchTopViewRanking() {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri())
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessTokenClient.getAccessToken())
                    .header("appkey", properties.appKey())
                    .header("appsecret", properties.appSecret())
                    .header("tr_id", properties.htsTopViewTrId())
                    .header("custtype", properties.customerType())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("KIS HTS top view request failed. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String resultCode = root.path("rt_cd").asText();
            if (StringUtils.hasText(resultCode) && !"0".equals(resultCode)) {
                throw new IllegalStateException("KIS HTS top view request failed. rt_cd=" + resultCode
                        + ", msg=" + root.path("msg1").asText());
            }

            return parseItems(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to request KIS HTS top view ranking", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while requesting KIS HTS top view ranking", exception);
        }
    }

    private URI uri() {
        String baseUrl = trimTrailingSlash(properties.baseUrl());
        String path = properties.htsTopViewPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String query = properties.htsTopViewQuery();
        if (StringUtils.hasText(query)) {
            return URI.create(baseUrl + path + "?" + query);
        }
        return URI.create(baseUrl + path);
    }

    private List<MarketRankingItemResponse> parseItems(JsonNode root) {
        JsonNode itemsNode = rankingItemsNode(root);
        List<MarketRankingItemResponse> items = new ArrayList<>();
        if (!itemsNode.isArray()) {
            return items;
        }

        int sequence = 1;
        for (JsonNode item : itemsNode) {
            int rank = parseRank(item, sequence);
            String stockCode = firstNonBlank(item,
                    "mksc_shrn_iscd", "stck_shrn_iscd", "pdno", "stock_code", "code");
            String stockName = firstNonBlank(item,
                    "hts_kor_isnm", "prdt_name", "stock_name", "name");
            String sign = firstNonBlank(item, "prdy_vrss_sign", "prdy_vrss_sign_name");
            BigDecimal changePrice = signedDecimal(item, sign,
                    "prdy_vrss", "prdy_vrss_amt", "change", "changePrice");
            BigDecimal changeRate = signedDecimal(item, sign,
                    "prdy_ctrt", "change_rate", "changeRate");

            items.add(new MarketRankingItemResponse(
                    rank,
                    stockCode,
                    StringUtils.hasText(stockName) ? stockName : stockCode,
                    decimal(item, "stck_prpr", "prpr", "current_price", "currentPrice"),
                    changePrice,
                    changeRate,
                    longValue(item, "acml_vol", "volume", "vol")
            ));
            sequence++;
        }
        return items;
    }

    private JsonNode rankingItemsNode(JsonNode root) {
        JsonNode output1 = root.path("output1");
        if (output1.isArray()) {
            return output1;
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            return output;
        }
        if (output.isObject()) {
            for (JsonNode child : output) {
                if (child.isArray()) {
                    return child;
                }
            }
        }
        JsonNode body = root.path("body");
        if (body.path("output1").isArray()) {
            return body.path("output1");
        }
        if (body.path("output").isArray()) {
            return body.path("output");
        }
        return objectMapper.createArrayNode();
    }

    private int parseRank(JsonNode item, int fallback) {
        String value = firstNonBlank(item, "data_rank", "rank", "hts_rank");
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(cleanNumber(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BigDecimal decimal(JsonNode item, String... fields) {
        String value = firstNonBlank(item, fields);
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(cleanNumber(value));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal signedDecimal(JsonNode item, String sign, String... fields) {
        BigDecimal value = decimal(item, fields);
        if (value.signum() < 0) {
            return value;
        }
        if ("4".equals(sign) || "5".equals(sign) || "-".equals(sign)) {
            return value.negate();
        }
        return value;
    }

    private long longValue(JsonNode item, String... fields) {
        String value = firstNonBlank(item, fields);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(cleanNumber(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String firstNonBlank(JsonNode item, String... fields) {
        for (String field : fields) {
            String value = item.path(field).asText();
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
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
