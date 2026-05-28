package com.mock.maesoongan.orderservice.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.kis.KisDtos.KisOrderRequest;
import com.mock.maesoongan.orderservice.kis.KisDtos.KisOrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class KisClient {

    private static final String ORDER_CASH_PATH = "/uapi/domestic-stock/v1/trading/order-cash";

    private final RestClient restClient;
    private final KisProperties properties;
    private final KisTokenService tokenService;

    public KisClient(
            RestClient.Builder restClientBuilder,
            KisProperties properties,
            KisTokenService tokenService
    ) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.tokenService = tokenService;
    }

    public KisOrderResponse orderCash(KisOrderRequest request) {
        properties.validateConfigured();

        String side = normalizeSide(request.side());
        String orderType = normalizeOrderType(request.orderType());
        Map<String, String> body = orderBody(request, orderType);
        String hashKey = issueHashKey(body);

        JsonNode response = restClient.post()
                .uri(ORDER_CASH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.setBearerAuth(tokenService.accessToken());
                    headers.set("appkey", properties.appKey());
                    headers.set("appsecret", properties.appSecret());
                    headers.set("tr_id", trId(side));
                    headers.set("custtype", properties.customerType());
                    headers.set("hashkey", hashKey);
                })
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "KIS_EMPTY_RESPONSE", "KIS API returned empty response");
        }
        if (!"0".equals(response.path("rt_cd").asText())) {
            throw new BusinessException(
                    HttpStatus.BAD_GATEWAY,
                    "KIS_ORDER_FAILED",
                    response.path("msg1").asText("KIS order failed")
            );
        }

        return new KisOrderResponse(
                response.path("rt_cd").asText(),
                response.path("msg_cd").asText(),
                response.path("msg1").asText(),
                response.path("output"),
                LocalDateTime.now()
        );
    }

    private Map<String, String> orderBody(KisOrderRequest request, String orderType) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("CANO", properties.accountNumber());
        body.put("ACNT_PRDT_CD", properties.accountProductCode());
        body.put("PDNO", normalizeStockCode(request.stockCode()));
        body.put("ORD_DVSN", orderDivision(orderType));
        body.put("ORD_QTY", String.valueOf(request.quantity()));
        body.put("ORD_UNPR", orderUnitPrice(orderType, request.price()));
        body.put("EXCG_ID_DVSN_CD", properties.exchangeId());
        body.put("SLL_TYPE", "");
        body.put("CNDT_PRIC", "");
        return body;
    }

    private String issueHashKey(Map<String, String> body) {
        JsonNode response = restClient.post()
                .uri("/uapi/hashkey")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("appkey", properties.appKey());
                    headers.set("appsecret", properties.appSecret());
                })
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String hash = response == null ? "" : response.path("HASH").asText("");
        if (hash.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "KIS_HASHKEY_FAILED", "Failed to issue KIS hashkey");
        }
        return hash;
    }

    private String trId(String side) {
        if ("BUY".equals(side)) {
            return properties.demo() ? "VTTC0012U" : "TTTC0012U";
        }
        return properties.demo() ? "VTTC0011U" : "TTTC0011U";
    }

    private String orderDivision(String orderType) {
        return "MARKET".equals(orderType) ? "01" : "00";
    }

    private String orderUnitPrice(String orderType, BigDecimal price) {
        if ("MARKET".equals(orderType)) {
            return "0";
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "price is required for LIMIT order");
        }
        return price.toPlainString();
    }

    private String normalizeStockCode(String stockCode) {
        return stockCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSide(String side) {
        String normalized = side.trim().toUpperCase(Locale.ROOT);
        if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "side must be BUY or SELL");
        }
        return normalized;
    }

    private String normalizeOrderType(String orderType) {
        String normalized = orderType.trim().toUpperCase(Locale.ROOT);
        if (!"LIMIT".equals(normalized) && !"MARKET".equals(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "orderType must be LIMIT or MARKET");
        }
        return normalized;
    }
}
