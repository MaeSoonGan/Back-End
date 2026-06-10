package com.mock.maesoongan.marketservice.marketdata.kis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class KisAccessTokenClient {

    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final KisMarketProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile CachedToken cachedToken;

    public KisAccessTokenClient(KisMarketProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public synchronized String getAccessToken() {
        CachedToken token = cachedToken;
        if (token != null && token.isValid()) {
            return token.value();
        }

        cachedToken = issueAccessToken();
        return cachedToken.value();
    }

    private CachedToken issueAccessToken() {
        validateRequired(properties.appKey(), "kis.app-key");
        validateRequired(properties.appSecret(), "kis.app-secret");

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "grant_type", "client_credentials",
                    "appkey", properties.appKey(),
                    "appsecret", properties.appSecret()
            ));
            HttpRequest request = HttpRequest.newBuilder(tokenUri())
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("KIS access token request failed. status=" + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.path("access_token").asText();
            validateRequired(accessToken, "access_token");

            long expiresIn = json.path("expires_in").asLong(86_400L);
            Instant expiresAt = Instant.now().plusSeconds(expiresIn).minus(TOKEN_EXPIRY_MARGIN);
            return new CachedToken(accessToken, expiresAt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to build KIS access token request", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to request KIS access token", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while requesting KIS access token", exception);
        }
    }

    private URI tokenUri() {
        if (StringUtils.hasText(properties.tokenUrl())) {
            return URI.create(properties.tokenUrl());
        }
        return URI.create(trimTrailingSlash(properties.baseUrl()) + "/oauth2/tokenP");
    }

    private String trimTrailingSlash(String value) {
        validateRequired(value, "kis.base-url");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void validateRequired(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
    }

    private record CachedToken(String value, Instant expiresAt) {

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
