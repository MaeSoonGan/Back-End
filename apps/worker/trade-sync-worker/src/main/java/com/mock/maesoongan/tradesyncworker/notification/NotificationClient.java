package com.mock.maesoongan.tradesyncworker.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * notification-api 내부 생성 엔드포인트(/api/internal/notifications) 호출 클라이언트.
 * 알림 생성 실패가 동기화 처리를 막지 않도록 best-effort(예외 무시 + 로그).
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;

    public NotificationClient(@Value("${notification.base-url:http://notification-api:8086}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void create(Long memberId, String type, String title, String body, String targetType, Long targetId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("memberId", memberId);
            payload.put("type", type);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("targetType", targetType);
            payload.put("targetId", targetId);

            HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(baseUrl) + "/api/internal/notifications"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.warn("Failed to create notification. type={}, memberId={}, err={}", type, memberId, exception.getMessage());
        }
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
