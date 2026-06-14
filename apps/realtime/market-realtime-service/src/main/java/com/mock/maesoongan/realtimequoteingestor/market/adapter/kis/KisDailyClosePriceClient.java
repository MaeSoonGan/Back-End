package com.mock.maesoongan.realtimequoteingestor.market.adapter.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisAccessTokenClient;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class KisDailyClosePriceClient {

    private static final Logger log = LoggerFactory.getLogger(KisDailyClosePriceClient.class);
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int LOOKBACK_DAYS = 14;

    private final KisProperties properties;
    private final KisAccessTokenClient accessTokenClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");

    public KisDailyClosePriceClient(
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

    public Optional<KisDailyCloseSnapshot> fetchLatestClose(String stockCode) {
        LocalDate to = LocalDate.now(zoneId);
        LocalDate from = to.minusDays(LOOKBACK_DAYS);

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
                log.warn("KIS daily close failed. code={}, status={}", stockCode, response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String resultCode = root.path("rt_cd").asText();
            if (StringUtils.hasText(resultCode) && !"0".equals(resultCode)) {
                log.warn("KIS daily close rt_cd={}, code={}, msg={}", resultCode, stockCode, root.path("msg1").asText());
                return Optional.empty();
            }

            String stockName = firstNonBlank(root.path("output1"), "hts_kor_isnm", "prdt_name", "stock_name");
            return parseOutput2(root.path("output2")).stream()
                    .filter(snapshot -> snapshot.closePrice().signum() > 0)
                    .max(Comparator.comparing(KisDailyCloseSnapshot::tradeDate))
                    .map(snapshot -> snapshot.withStockName(StringUtils.hasText(stockName) ? stockName : stockCode));
        } catch (IOException exception) {
            log.warn("KIS daily close request failed. code={}, err={}", stockCode, exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("KIS daily close request failed. code={}, err={}", stockCode, exception.getMessage());
            return Optional.empty();
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

    private List<KisDailyCloseSnapshot> parseOutput2(JsonNode output2) {
        List<KisDailyCloseSnapshot> rows = new ArrayList<>();
        if (!output2.isArray()) {
            return rows;
        }

        for (JsonNode item : output2) {
            String dateText = firstNonBlank(item, "stck_bsop_date", "bsop_date", "trade_date");
            if (!StringUtils.hasText(dateText)) {
                continue;
            }

            try {
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

                BigDecimal changeRate = decimal(item, "prdy_ctrt", "change_rate");
                if (changeRate.signum() == 0 && prevClose.signum() > 0) {
                    changeRate = change
                            .multiply(BigDecimal.valueOf(100))
                            .divide(prevClose, 2, RoundingMode.HALF_UP);
                }

                long volume = longValue(item, "acml_vol", "volume");
                BigDecimal tradingValue = decimal(item, "acml_tr_pbmn", "trading_value", "accumulated_trading_value");
                if (tradingValue.signum() == 0 && close.signum() > 0 && volume > 0) {
                    tradingValue = close.multiply(BigDecimal.valueOf(volume));
                }

                rows.add(new KisDailyCloseSnapshot(
                        "",
                        tradeDate,
                        close,
                        change,
                        changeRate,
                        changeSign(sign, change),
                        volume,
                        tradingValue,
                        decimal(item, "stck_hgpr", "hgpr", "high_price", "high"),
                        decimal(item, "stck_lwpr", "lwpr", "low_price", "low"),
                        decimal(item, "stck_oprc", "oprc", "open_price", "open")
                ));
            } catch (DateTimeParseException exception) {
                log.warn("KIS daily close row skipped. date={}", dateText);
            }
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
        return value.replace(",", "").replace("+", "").replace("%", "").trim();
    }

    private String changeSign(String sign, BigDecimal changePrice) {
        if ("1".equals(sign) || "2".equals(sign) || "+".equals(sign)) {
            return "+";
        }
        if ("4".equals(sign) || "5".equals(sign) || "-".equals(sign)) {
            return "-";
        }
        int signum = changePrice.signum();
        if (signum > 0) {
            return "+";
        }
        if (signum < 0) {
            return "-";
        }
        return "0";
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

    public record KisDailyCloseSnapshot(
            String stockName,
            LocalDate tradeDate,
            BigDecimal closePrice,
            BigDecimal changePrice,
            BigDecimal changeRate,
            String changeSign,
            long volume,
            BigDecimal tradingValue,
            BigDecimal high,
            BigDecimal low,
            BigDecimal open
    ) {

        KisDailyCloseSnapshot withStockName(String stockName) {
            return new KisDailyCloseSnapshot(
                    stockName,
                    tradeDate,
                    closePrice,
                    changePrice,
                    changeRate,
                    changeSign,
                    volume,
                    tradingValue,
                    high,
                    low,
                    open
            );
        }
    }
}
