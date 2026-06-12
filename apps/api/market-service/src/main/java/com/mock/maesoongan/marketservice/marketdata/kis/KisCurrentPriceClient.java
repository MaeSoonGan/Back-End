package com.mock.maesoongan.marketservice.marketdata.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * KIS 현재가 조회(inquire-price, FHKST01010100) REST 클라이언트.
 * 관심종목 가격 스냅샷 적재용 — REST라 ws 등록 한도와 무관.
 */
@Component
public class KisCurrentPriceClient {

    private static final Logger log = LoggerFactory.getLogger(KisCurrentPriceClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KisAccessTokenClient accessTokenClient;
    private final KisMarketProperties properties;

    public KisCurrentPriceClient(KisAccessTokenClient accessTokenClient, KisMarketProperties properties) {
        this.accessTokenClient = accessTokenClient;
        this.properties = properties;
    }

    public Optional<CurrentPrice> fetchPrice(String stockCode) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(stockCode))
                    .timeout(Duration.ofSeconds(5))
                    .header("content-type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessTokenClient.getAccessToken())
                    .header("appkey", properties.appKey())
                    .header("appsecret", properties.appSecret())
                    .header("tr_id", properties.inquirePriceTrId())
                    .header("custtype", properties.customerType())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"0".equals(root.path("rt_cd").asText())) {
                return Optional.empty();
            }
            JsonNode output = root.path("output");
            String sign = output.path("prdy_vrss_sign").asText("");
            return Optional.of(new CurrentPrice(
                    decimal(output, "stck_prpr"),
                    signed(sign, decimal(output, "prdy_vrss")),
                    signed(sign, decimal(output, "prdy_ctrt")),
                    output.path("acml_vol").asLong(0)
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("KIS inquire-price failed. code={}, err={}", stockCode, exception.getMessage());
            return Optional.empty();
        }
    }

    private URI uri(String stockCode) {
        String path = properties.inquirePricePath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(properties.baseUrl() + path
                + "?FID_COND_MRKT_DIV_CODE=" + properties.dailyChartMarketDivCode()
                + "&FID_INPUT_ISCD=" + stockCode);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    // KIS prdy_vrss_sign: 4(하한)/5(하락) → 음수
    private BigDecimal signed(String sign, BigDecimal value) {
        return ("4".equals(sign) || "5".equals(sign)) ? value.negate() : value;
    }

    public record CurrentPrice(BigDecimal price, BigDecimal change, BigDecimal changeRate, long volume) {
    }
}
