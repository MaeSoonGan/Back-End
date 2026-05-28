package com.mock.maesoongan.orderservice.kis;

import com.mock.maesoongan.orderservice.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class KisProperties {

    private final String baseUrl;
    private final String environment;
    private final String appKey;
    private final String appSecret;
    private final String accountNumber;
    private final String accountProductCode;
    private final String customerType;
    private final String exchangeId;
    private final Duration tokenCacheTtl;

    public KisProperties(
            @Value("${app.kis.base-url}") String baseUrl,
            @Value("${app.kis.environment}") String environment,
            @Value("${app.kis.app-key}") String appKey,
            @Value("${app.kis.app-secret}") String appSecret,
            @Value("${app.kis.account-number}") String accountNumber,
            @Value("${app.kis.account-product-code}") String accountProductCode,
            @Value("${app.kis.customer-type}") String customerType,
            @Value("${app.kis.exchange-id}") String exchangeId,
            @Value("${app.kis.token-cache-ttl}") Duration tokenCacheTtl
    ) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.environment = environment;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.accountNumber = accountNumber;
        this.accountProductCode = accountProductCode;
        this.customerType = customerType;
        this.exchangeId = exchangeId;
        this.tokenCacheTtl = tokenCacheTtl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String environment() {
        return environment;
    }

    public String appKey() {
        return appKey;
    }

    public String appSecret() {
        return appSecret;
    }

    public String accountNumber() {
        return accountNumber;
    }

    public String accountProductCode() {
        return accountProductCode;
    }

    public String customerType() {
        return customerType;
    }

    public String exchangeId() {
        return exchangeId;
    }

    public Duration tokenCacheTtl() {
        return tokenCacheTtl;
    }

    public boolean demo() {
        return "demo".equalsIgnoreCase(environment) || "vps".equalsIgnoreCase(environment);
    }

    public void validateConfigured() {
        if (isBlank(appKey) || isBlank(appSecret) || isBlank(accountNumber) || isBlank(accountProductCode)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "KIS_NOT_CONFIGURED", "KIS API credentials are not configured");
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
