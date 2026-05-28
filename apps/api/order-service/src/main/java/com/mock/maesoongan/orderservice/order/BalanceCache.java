package com.mock.maesoongan.orderservice.order;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Component
public class BalanceCache {

    private static final String RESERVE_SCRIPT = """
            local balance = redis.call('GET', KEYS[1])
            if not balance then
                return -1
            end
            local pending = redis.call('GET', KEYS[2])
            local current = tonumber(balance)
            local pendingRelease = tonumber(pending or '0')
            local amount = tonumber(ARGV[1])
            if current + pendingRelease < amount then
                return 0
            end
            redis.call('INCRBYFLOAT', KEYS[1], '-' .. ARGV[1])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveScript;

    public BalanceCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = new DefaultRedisScript<>(RESERVE_SCRIPT, Long.class);
    }

    public void cacheBalance(long memberId, long contestId, BigDecimal amount) {
        redisTemplate.opsForValue().set(balanceKey(memberId, contestId), amount.toPlainString());
    }

    public ReserveResult reserve(long memberId, long contestId, BigDecimal amount) {
        Long result = redisTemplate.execute(
                reserveScript,
                List.of(balanceKey(memberId, contestId), pendingReleaseKey(memberId, contestId)),
                amount.toPlainString()
        );
        if (result == null || result < 0) {
            return ReserveResult.MISSING;
        }
        if (result == 0) {
            return ReserveResult.INSUFFICIENT;
        }
        return ReserveResult.RESERVED;
    }

    public void releaseReservation(long memberId, long contestId, BigDecimal amount) {
        redisTemplate.opsForValue().increment(balanceKey(memberId, contestId), amount.doubleValue());
    }

    public void markCancelPending(long memberId, long contestId, long orderId, BigDecimal pendingAmount, Duration ttl) {
        redisTemplate.opsForValue().set("cancel:pending:" + orderId, "true", ttl);
        if (pendingAmount.compareTo(BigDecimal.ZERO) > 0) {
            String key = pendingReleaseKey(memberId, contestId);
            redisTemplate.opsForValue().increment(key, pendingAmount.doubleValue());
            redisTemplate.expire(key, ttl);
        }
    }

    private String balanceKey(long memberId, long contestId) {
        return "balance:" + memberId + ":" + contestId;
    }

    private String pendingReleaseKey(long memberId, long contestId) {
        return "balance:pending-release:" + memberId + ":" + contestId;
    }

    public enum ReserveResult {
        RESERVED,
        INSUFFICIENT,
        MISSING
    }
}
