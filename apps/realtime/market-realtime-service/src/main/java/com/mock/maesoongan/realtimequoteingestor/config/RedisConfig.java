package com.mock.maesoongan.realtimequoteingestor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer redisSslCustomizer(
            @Value("${redis.ssl-verify-peer:true}") boolean sslVerifyPeer
    ) {
        return builder -> {
            if (sslVerifyPeer) {
                return;
            }

            builder.useSsl().disablePeerVerification().and();
        };
    }
}
