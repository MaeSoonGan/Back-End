package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.common.BusinessException;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceSummary;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPricesResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MarketPriceService {

    private static final String PRICE_KEY_PREFIX = "stock:";
    private static final String PRICE_KEY_SUFFIX = ":price";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MarketPriceService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public MarketPriceResponse getPrice(String stockCode) {
        return findPrice(stockCode)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_NOT_FOUND",
                        "시세 데이터가 존재하지 않는 종목코드입니다: " + stockCode
                ));
    }

    public MarketPricesResponse getPrices(String codes) {
        if (!StringUtils.hasText(codes)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "codes query parameter is required");
        }

        List<MarketPriceSummary> prices = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        for (String rawCode : codes.split(",")) {
            String stockCode = rawCode.trim();
            if (!StringUtils.hasText(stockCode)) {
                continue;
            }
            Optional<MarketPriceResponse> price = findPrice(stockCode);
            if (price.isPresent()) {
                prices.add(MarketPriceSummary.from(price.get()));
            } else {
                notFound.add(stockCode);
            }
        }

        return new MarketPricesResponse(prices, notFound);
    }

    public Optional<MarketPriceResponse> findPrice(String stockCode) {
        String json = redisTemplate.opsForValue().get(priceKey(stockCode));
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MarketPriceResponse.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize market price", exception);
        }
    }

    public static String priceKey(String stockCode) {
        return PRICE_KEY_PREFIX + stockCode + PRICE_KEY_SUFFIX;
    }
}
