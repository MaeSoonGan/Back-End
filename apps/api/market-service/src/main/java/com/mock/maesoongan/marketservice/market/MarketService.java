package com.mock.maesoongan.marketservice.market;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.market.MarketDtos.HtsTopViewRankingItem;
import com.mock.maesoongan.marketservice.market.MarketDtos.HtsTopViewRankingResponse;
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
import java.util.Optional;

@Service
public class MarketService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(15, 30);
    private static final String RANKING_TYPE_TRADING_VALUE = "TRADING_VALUE";
    private static final String RANKING_TYPE_RISE = "RISE";
    private static final String RANKING_TYPE_FALL = "FALL";
    private static final Map<String, String> RANKING_TYPES = Map.of(
            "\uAC70\uB798\uB300\uAE08", RANKING_TYPE_TRADING_VALUE,
            RANKING_TYPE_TRADING_VALUE, RANKING_TYPE_TRADING_VALUE,
            "\uC0C1\uC2B9", RANKING_TYPE_RISE,
            RANKING_TYPE_RISE, RANKING_TYPE_RISE,
            "\uD558\uB77D", RANKING_TYPE_FALL,
            RANKING_TYPE_FALL, RANKING_TYPE_FALL
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

    // KOSPI/KOSDAQ 지수를 RDS 스냅샷(market_index_snapshot, 마지막 적재값 유지)에서 조회.
    // 라이브 캐시가 비어도 마지막 실제값을 돌려주므로 화면에 "-"/빈값이 뜨지 않는다.
    @Transactional(readOnly = true)
    public List<MarketIndexResponse> getMarketIndices() {
        return List.of("KOSPI", "KOSDAQ").stream()
                .map(this::normalizeIndexMarket)
                .map(marketDataRepository::findMarketIndex)
                .flatMap(Optional::stream)
                .map(row -> new MarketIndexResponse(
                        row.market(),
                        row.value(),
                        row.change(),
                        row.changeRate(),
                        row.cached()
                ))
                .toList();
    }

    // 실시간 조회상위 순위 — RDS 스냅샷(HTS_TOP_VIEW) 조회. findMarketRankings가 stock을 조인해 종목명을 채워준다.
    @Transactional(readOnly = true)
    public HtsTopViewRankingResponse getHtsTopViewRanking() {
        List<HtsTopViewRankingItem> items = marketDataRepository.findMarketRankings("HTS_TOP_VIEW").stream()
                .map(row -> new HtsTopViewRankingItem(
                        row.rank(),
                        row.code(),
                        row.name(),
                        row.price(),
                        row.change(),
                        row.changeRate(),
                        row.volume()
                ))
                .toList();
        return new HtsTopViewRankingResponse(items);
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
            return RANKING_TYPE_TRADING_VALUE;
        }

        String normalized = type.trim();
        String rankingType = RANKING_TYPES.get(normalized);
        if (rankingType == null) {
            rankingType = RANKING_TYPES.get(normalized.toUpperCase(Locale.ROOT));
        }
        if (rankingType == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "type must be trading value, rise, or fall");
        }
        return rankingType;
    }
}
