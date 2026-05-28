package com.mock.maesoongan.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer redisSslPeerVerificationCustomizer(
            @Value("${app.redis.ssl.verify-peer:true}") boolean verifyPeer
    ) {
        return builder -> {
            if (!verifyPeer) {
                builder.useSsl().disablePeerVerification().and();
            }
        };
    }
}
