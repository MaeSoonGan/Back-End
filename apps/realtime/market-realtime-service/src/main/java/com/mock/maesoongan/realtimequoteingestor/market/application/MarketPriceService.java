package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.common.BusinessException;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisCurrentPriceClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceSummary;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPricesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class MarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceService.class);
    private static final String PRICE_KEY_PREFIX = "stock:";
    private static final String PRICE_KEY_SUFFIX = ":price";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KisCurrentPriceClient kisCurrentPriceClient;
    private final Duration quoteTtl;

    public MarketPriceService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            KisCurrentPriceClient kisCurrentPriceClient,
            @Value("${redis.quote-ttl-seconds:300}") long quoteTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.kisCurrentPriceClient = kisCurrentPriceClient;
        this.quoteTtl = Duration.ofSeconds(quoteTtlSeconds);
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
        Optional<MarketPriceResponse> cachedPrice = findCachedPrice(stockCode);
        if (cachedPrice.isPresent()) {
            return cachedPrice;
        }
        return fetchAndCachePrice(stockCode);
    }

    private Optional<MarketPriceResponse> findCachedPrice(String stockCode) {
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

    private Optional<MarketPriceResponse> fetchAndCachePrice(String stockCode) {
        Optional<MarketPriceResponse> response = kisCurrentPriceClient.fetchPrice(stockCode)
                .map(snapshot -> toResponse(stockCode, snapshot));
        response.ifPresent(this::cachePrice);
        return response;
    }

    private MarketPriceResponse toResponse(String stockCode, KisCurrentPriceClient.KisPriceSnapshot snapshot) {
        String stockName = StringUtils.hasText(snapshot.stockName()) ? snapshot.stockName() : stockCode;
        return new MarketPriceResponse(
                stockCode,
                stockName,
                snapshot.currentPrice(),
                snapshot.changePrice(),
                snapshot.changeRate(),
                snapshot.changeSign(),
                snapshot.volume(),
                snapshot.tradingValue(),
                snapshot.high(),
                snapshot.low(),
                snapshot.open(),
                snapshot.timestamp()
        );
    }

    private void cachePrice(MarketPriceResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    priceKey(response.stockCode()),
                    objectMapper.writeValueAsString(response),
                    quoteTtl
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize market price", exception);
        } catch (RuntimeException exception) {
            log.warn("Failed to cache market price. code={}, err={}", response.stockCode(), exception.getMessage());
        }
    }

    public static String priceKey(String stockCode) {
        return PRICE_KEY_PREFIX + stockCode + PRICE_KEY_SUFFIX;
    }
}
