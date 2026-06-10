package com.mock.maesoongan.marketservice.marketdata.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class KisDailyItemChartClient {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisMarketProperties properties;
    private final KisAccessTokenClient accessTokenClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KisDailyItemChartClient(
            KisMarketProperties properties,
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

    public List<DailyChartPrice> fetchDailyPrices(String stockCode, LocalDate from, LocalDate to) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(stockCode, from, to))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessTokenClient.getAccessToken())
                    .header("appkey", properties.appKey())
                    .header("appsecret", properties.appSecret())
                    .header("tr_id", properties.dailyChartTrId())
                    .header("custtype", properties.customerType())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("KIS daily item chart request failed. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String resultCode = root.path("rt_cd").asText();
            if (StringUtils.hasText(resultCode) && !"0".equals(resultCode)) {
                throw new IllegalStateException("KIS daily item chart request failed. rt_cd=" + resultCode
                        + ", msg=" + root.path("msg1").asText());
            }
            return parseOutput2(root.path("output2"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to request KIS daily item chart", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while requesting KIS daily item chart", exception);
        }
    }

    private URI uri(String stockCode, LocalDate from, LocalDate to) {
        String baseUrl = trimTrailingSlash(properties.baseUrl());
        String path = properties.dailyChartPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String query = "FID_COND_MRKT_DIV_CODE=" + encode(properties.dailyChartMarketDivCode())
                + "&FID_INPUT_ISCD=" + encode(stockCode)
                + "&FID_INPUT_DATE_1=" + encode(from.format(BASIC_DATE))
                + "&FID_INPUT_DATE_2=" + encode(to.format(BASIC_DATE))
                + "&FID_PERIOD_DIV_CODE=D"
                + "&FID_ORG_ADJ_PRC=" + encode(properties.dailyChartAdjustedPrice());
        return URI.create(baseUrl + path + "?" + query);
    }

    private List<DailyChartPrice> parseOutput2(JsonNode output2) {
        List<DailyChartPrice> rows = new ArrayList<>();
        if (!output2.isArray()) {
            return rows;
        }

        for (JsonNode item : output2) {
            String dateText = firstNonBlank(item, "stck_bsop_date", "bsop_date", "trade_date");
            if (!StringUtils.hasText(dateText)) {
                continue;
            }

            LocalDate tradeDate = LocalDate.parse(dateText, BASIC_DATE);
            BigDecimal close = decimal(item, "stck_clpr", "clpr", "close_price", "close");
            String sign = firstNonBlank(item, "prdy_vrss_sign", "prdy_vrss_sign_name");
            BigDecimal change = signedDecimal(item, sign, "prdy_vrss", "change", "change_price");
            BigDecimal prevClose = decimal(item, "stck_prdy_clpr", "prdy_clpr", "prev_close_price");
            if (prevClose.signum() == 0 && change.signum() != 0) {
                prevClose = close.subtract(change);
            }
            if (prevClose.signum() == 0) {
                prevClose = close;
            }

            rows.add(new DailyChartPrice(
                    tradeDate,
                    decimal(item, "stck_oprc", "oprc", "open_price", "open"),
                    decimal(item, "stck_hgpr", "hgpr", "high_price", "high"),
                    decimal(item, "stck_lwpr", "lwpr", "low_price", "low"),
                    close,
                    prevClose,
                    longValue(item, "acml_vol", "volume")
            ));
        }
        return rows;
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record DailyChartPrice(
            LocalDate tradeDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal prevClosePrice,
            long volume
    ) {
    }
}
