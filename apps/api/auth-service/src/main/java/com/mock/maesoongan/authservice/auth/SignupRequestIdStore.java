package com.mock.maesoongan.authservice.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SignupRequestIdStore {

    private static final Duration EXPIRES_IN = Duration.ofMinutes(30);

    private final Map<String, Entry> requestIds = new ConcurrentHashMap<>();

    public String getOrIssue(String email, String loginId) {
        Instant now = Instant.now();
        String key = key(email, loginId);
        requestIds.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        return requestIds.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return new Entry(UUID.randomUUID().toString(), now.plus(EXPIRES_IN));
            }
            return existing;
        }).requestId();
    }

    private String key(String email, String loginId) {
        return normalize(email) + ":" + normalize(loginId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record Entry(String requestId, Instant expiresAt) {
    }
}
