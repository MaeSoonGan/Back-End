package com.mock.maesoongan.orderservice.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.mock.maesoongan.orderservice.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class KisTokenService {

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final KisProperties properties;

    public KisTokenService(
            RestClient.Builder restClientBuilder,
            StringRedisTemplate redisTemplate,
            KisProperties properties
    ) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public String accessToken() {
        properties.validateConfigured();

        String cacheKey = "kis:access-token:" + properties.environment();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        JsonNode response = restClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", properties.appKey(),
                        "appsecret", properties.appSecret()
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("access_token").asText("").isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "KIS_AUTH_FAILED", "Failed to issue KIS access token");
        }

        String token = response.path("access_token").asText();
        redisTemplate.opsForValue().set(cacheKey, token, properties.tokenCacheTtl());
        return token;
    }
}
