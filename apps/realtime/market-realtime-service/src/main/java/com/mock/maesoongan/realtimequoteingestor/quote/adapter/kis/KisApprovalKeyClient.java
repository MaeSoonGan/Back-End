package com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis;

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
import java.util.Map;

@Component
public class KisApprovalKeyClient {

    private final KisProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KisApprovalKeyClient(KisProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String issueApprovalKey() {
        validateRequired(properties.appKey(), "kis.app-key");
        validateRequired(properties.appSecret(), "kis.app-secret");
        validateRequired(properties.approvalUrl(), "kis.approval-url");

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "grant_type", "client_credentials",
                    "appkey", properties.appKey(),
                    "secretkey", properties.appSecret()
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.approvalUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("KIS approval key request failed. status=" + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String approvalKey = json.path("approval_key").asText();
            validateRequired(approvalKey, "approval_key");
            return approvalKey;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to build KIS approval key request", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to request KIS approval key", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while requesting KIS approval key", exception);
        }
    }

    private void validateRequired(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
    }
}
