package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.common.BusinessException;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisCurrentPriceClient;
import com.mock.maesoongan.realtimequoteingestor.market.adapter.kis.KisHtsTopViewClient;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketRankingItemResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketRankingResponse;
import com.mock.maesoongan.realtimequoteingestor.stock.StockNameResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class MarketRankingService {

    public static final String HTS_TOP_VIEW_KEY = "market:ranking:hts-top-view";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KisHtsTopViewClient kisHtsTopViewClient;
    private final KisCurrentPriceClient kisCurrentPriceClient;
    private final StockNameResolver stockNameResolver;
    private final Duration ttl;
    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");

    public MarketRankingService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            KisHtsTopViewClient kisHtsTopViewClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            StockNameResolver stockNameResolver,
            @Value("${ranking.hts-top-view.cache-ttl-seconds:30}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.kisHtsTopViewClient = kisHtsTopViewClient;
        this.kisCurrentPriceClient = kisCurrentPriceClient;
        this.stockNameResolver = stockNameResolver;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public MarketRankingResponse getHtsTopViewRanking() {
        String json = redisTemplate.opsForValue().get(HTS_TOP_VIEW_KEY);
        if (!StringUtils.hasText(json)) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "RANKING_NOT_FOUND",
                    "실시간 조회 상위 종목 캐시가 아직 없습니다."
            );
        }

        try {
            return objectMapper.readValue(json, MarketRankingResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize market ranking", exception);
        }
    }

    public MarketRankingResponse refreshHtsTopViewRanking() {
        List<MarketRankingItemResponse> items = kisHtsTopViewClient.fetchTopViewRanking();
        if (items.isEmpty()) {
            return getHtsTopViewRanking();
        }

        // KIS 조회상위 응답은 순위+코드만 줘서(가격 0, 이름=코드) 종목별 현재가 조회(REST)로 가격·이름을 보강한다.
        List<MarketRankingItemResponse> enriched = items.stream()
                .map(this::enrichItem)
                .toList();

        MarketRankingResponse response = new MarketRankingResponse(
                enriched,
                true,
                LocalDateTime.now(zoneId).withNano(0)
        );
        redisTemplate.opsForValue().set(HTS_TOP_VIEW_KEY, toJson(response), ttl);
        return response;
    }

    // 종목별 현재가 조회(REST)로 가격·이름 보강. 조회 실패 시 원본 + 종목마스터 이름으로 폴백.
    private MarketRankingItemResponse enrichItem(MarketRankingItemResponse item) {
        return kisCurrentPriceClient.fetchPrice(item.stockCode())
                .map(price -> new MarketRankingItemResponse(
                        item.rank(),
                        item.stockCode(),
                        StringUtils.hasText(price.stockName())
                                ? price.stockName()
                                : stockNameResolver.resolve(item.stockCode(), item.stockName()),
                        price.currentPrice(),
                        price.changePrice(),
                        price.changeRate(),
                        price.volume()
                ))
                .orElseGet(() -> new MarketRankingItemResponse(
                        item.rank(),
                        item.stockCode(),
                        stockNameResolver.resolve(item.stockCode(), item.stockName()),
                        item.currentPrice(),
                        item.changePrice(),
                        item.changeRate(),
                        item.volume()
                ));
    }

    private String toJson(MarketRankingResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize market ranking", exception);
        }
    }
}
