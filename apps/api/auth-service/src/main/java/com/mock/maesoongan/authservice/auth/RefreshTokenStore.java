package com.mock.maesoongan.authservice.auth;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenStore {

    private final Map<String, Set<String>> tokensByUserId = new ConcurrentHashMap<>();

    public void save(String userId, String refreshToken) {
        tokensByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(refreshToken);
    }

    public boolean isActive(String userId, String refreshToken) {
        return tokensByUserId.getOrDefault(userId, Set.of()).contains(refreshToken);
    }

    public void replace(String userId, String oldRefreshToken, String newRefreshToken) {
        Set<String> tokens = tokensByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
        tokens.remove(oldRefreshToken);
        tokens.add(newRefreshToken);
    }

    public void revokeAll(String userId) {
        tokensByUserId.remove(userId);
    }

    public int countByUserId(String userId) {
        return tokensByUserId.getOrDefault(userId, Set.of()).size();
    }
}
