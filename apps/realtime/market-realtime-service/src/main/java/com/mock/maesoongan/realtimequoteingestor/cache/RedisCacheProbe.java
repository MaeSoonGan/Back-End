package com.mock.maesoongan.realtimequoteingestor.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RedisCacheProbe {

    private final boolean redisEnabled;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public RedisCacheProbe(
            @Value("${redis.enabled:false}") boolean redisEnabled,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        this.redisEnabled = redisEnabled;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public RedisConnectionStatus status() {
        if (!redisEnabled) {
            return new RedisConnectionStatus(false, false, "Redis cache is disabled", LocalDateTime.now());
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return new RedisConnectionStatus(true, false, "RedisTemplate is not available", LocalDateTime.now());
        }

        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            return new RedisConnectionStatus(true, true, pong, LocalDateTime.now());
        } catch (RuntimeException exception) {
            return new RedisConnectionStatus(true, false, rootCauseMessage(exception), LocalDateTime.now());
        }
    }

    public RedisCacheValue get(String key) {
        if (!redisEnabled) {
            return new RedisCacheValue(false, false, key, null, "Redis cache is disabled", LocalDateTime.now());
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return new RedisCacheValue(true, false, key, null, "RedisTemplate is not available", LocalDateTime.now());
        }

        try {
            String value = redisTemplate.opsForValue().get(key);
            return new RedisCacheValue(true, true, key, value, null, LocalDateTime.now());
        } catch (RuntimeException exception) {
            return new RedisCacheValue(true, false, key, null, rootCauseMessage(exception), LocalDateTime.now());
        }
    }

    private String rootCauseMessage(RuntimeException exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? exception.getMessage() : cause.getMessage();
    }

    public record RedisConnectionStatus(
            boolean enabled,
            boolean connected,
            String message,
            LocalDateTime checkedAt
    ) {
    }

    public record RedisCacheValue(
            boolean enabled,
            boolean connected,
            String key,
            String value,
            String errorMessage,
            LocalDateTime checkedAt
    ) {
    }
}
