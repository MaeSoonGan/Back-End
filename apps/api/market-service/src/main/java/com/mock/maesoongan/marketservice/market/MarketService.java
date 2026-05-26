package com.mock.maesoongan.marketservice.market;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketIndexResponse;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketRankingItem;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketStatusResponse;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.MarketIndexRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(15, 30);
    private static final Map<String, String> RANKING_TYPES = Map.of(
            "거래대금", "TRADING_VALUE",
            "상승", "RISE",
            "하락", "FALL"
    );

    private final MarketDataRepository marketDataRepository;

    public MarketService(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    @Transactional(readOnly = true)
    public MarketIndexResponse getMarketIndex(String market) {
        String normalizedMarket = normalizeIndexMarket(market);
        MarketIndexRow row = marketDataRepository.findMarketIndex(normalizedMarket)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Market index not found"));

        return new MarketIndexResponse(
                row.market(),
                row.value(),
                row.change(),
                row.changeRate(),
                row.cached()
        );
    }

    public MarketStatusResponse getMarketStatus() {
        LocalDateTime now = LocalDateTime.now(KST);
        boolean weekday = now.getDayOfWeek() != DayOfWeek.SATURDAY && now.getDayOfWeek() != DayOfWeek.SUNDAY;
        boolean open = weekday && !now.toLocalTime().isBefore(OPEN_TIME) && now.toLocalTime().isBefore(CLOSE_TIME);

        return new MarketStatusResponse(
                open ? "open" : "closed",
                "09:00",
                "15:30",
                null
        );
    }

    @Transactional(readOnly = true)
    public List<MarketRankingItem> getMarketRanking(String type) {
        String rankingType = normalizeRankingType(type);
        return marketDataRepository.findMarketRankings(rankingType)
                .stream()
                .map(row -> new MarketRankingItem(
                        row.rank(),
                        row.code(),
                        row.name(),
                        row.market(),
                        row.price(),
                        row.change(),
                        row.changeRate(),
                        row.volume()
                ))
                .toList();
    }

    private String normalizeIndexMarket(String market) {
        if (market == null || market.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "market is required");
        }

        String normalized = market.trim().toUpperCase(Locale.ROOT);
        if (!"KOSPI".equals(normalized) && !"KOSDAQ".equals(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "market must be KOSPI or KOSDAQ");
        }
        return normalized;
    }

    private String normalizeRankingType(String type) {
        if (type == null || type.isBlank()) {
            return RANKING_TYPES.get("거래대금");
        }

        String normalized = type.trim();
        String rankingType = RANKING_TYPES.get(normalized);
        if (rankingType == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "type must be 거래대금, 상승, or 하락");
        }
        return rankingType;
    }
}
