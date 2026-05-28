package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
public class MarketStatusService {

    public static final String MARKET_STATUS_KEY = "market:status";
    private static final LocalTime OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(15, 30);
    private static final LocalTime PRE_MARKET_START_TIME = LocalTime.of(8, 0);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");

    public MarketStatusService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${redis.market-status-ttl-seconds:60}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public MarketStatusResponse currentStatus() {
        LocalDate today = LocalDate.now(zoneId);
        LocalTime now = LocalTime.now(zoneId).withNano(0);
        MarketStatusType status = calculateStatus(today, now);
        MarketStatusResponse response = new MarketStatusResponse(
                status,
                description(status),
                OPEN_TIME,
                CLOSE_TIME,
                now,
                status == MarketStatusType.OPEN
        );
        cache(response);
        return response;
    }

    private MarketStatusType calculateStatus(LocalDate date, LocalTime time) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return MarketStatusType.CLOSED;
        }
        if (!time.isBefore(OPEN_TIME) && !time.isAfter(CLOSE_TIME)) {
            return MarketStatusType.OPEN;
        }
        if (!time.isBefore(PRE_MARKET_START_TIME) && time.isBefore(OPEN_TIME)) {
            return MarketStatusType.PRE_MARKET;
        }
        return MarketStatusType.POST_MARKET;
    }

    private String description(MarketStatusType status) {
        return switch (status) {
            case PRE_MARKET -> "장전";
            case OPEN -> "장중";
            case POST_MARKET -> "장후";
            case CLOSED -> "휴장일";
        };
    }

    private void cache(MarketStatusResponse response) {
        try {
            redisTemplate.opsForValue().set(MARKET_STATUS_KEY, objectMapper.writeValueAsString(response), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize market status", exception);
        } catch (RuntimeException ignored) {
            // Market status must remain available even when Redis is temporarily unavailable.
        }
    }
}
