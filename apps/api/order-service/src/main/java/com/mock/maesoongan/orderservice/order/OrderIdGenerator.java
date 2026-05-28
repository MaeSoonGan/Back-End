package com.mock.maesoongan.orderservice.order;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderIdGenerator {

    private final StringRedisTemplate redisTemplate;

    public OrderIdGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nextId() {
        Long sequence = redisTemplate.opsForValue().increment("order:id:sequence");
        long suffix = sequence == null ? 0 : Math.floorMod(sequence, 1_000);
        return Instant.now().toEpochMilli() * 1_000 + suffix;
    }
}
