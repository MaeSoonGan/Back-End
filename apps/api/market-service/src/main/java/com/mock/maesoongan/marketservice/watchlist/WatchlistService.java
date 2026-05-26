package com.mock.maesoongan.marketservice.watchlist;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.AddWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.DeleteWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.WatchlistItem;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.WatchlistResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class WatchlistService {

    private static final int WATCHLIST_LIMIT = 30;

    private final MarketDataRepository marketDataRepository;

    public WatchlistService(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    @Transactional(readOnly = true)
    public WatchlistResponse getWatchlist(Long memberId, String market) {
        List<String> markets = marketsByTab(market);
        List<WatchlistItem> items = marketDataRepository.findWatchlistStocks(memberId, markets)
                .stream()
                .map(row -> new WatchlistItem(
                        row.code(),
                        row.name(),
                        row.market(),
                        row.price(),
                        row.change(),
                        row.changeRate()
                ))
                .toList();

        return new WatchlistResponse(items.size(), items);
    }

    @Transactional
    public AddWatchlistResponse addWatchlist(Long memberId, String stockCode) {
        String normalizedCode = normalizeCode(stockCode);
        if (!marketDataRepository.existsActiveStock(normalizedCode)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Stock not found");
        }
        if (marketDataRepository.existsWatchlist(memberId, normalizedCode)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONFLICT", "Stock already exists in watchlist");
        }
        if (marketDataRepository.countWatchlist(memberId) >= WATCHLIST_LIMIT) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONFLICT", "Watchlist limit exceeded");
        }

        marketDataRepository.insertWatchlist(memberId, normalizedCode);
        return new AddWatchlistResponse(normalizedCode, marketDataRepository.countWatchlist(memberId));
    }

    @Transactional
    public DeleteWatchlistResponse deleteWatchlist(Long memberId, String stockCode) {
        String normalizedCode = normalizeCode(stockCode);
        if (!marketDataRepository.existsWatchlist(memberId, normalizedCode)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Watchlist stock not found");
        }

        marketDataRepository.deleteWatchlist(memberId, normalizedCode);
        return new DeleteWatchlistResponse(marketDataRepository.countWatchlist(memberId));
    }

    private List<String> marketsByTab(String market) {
        String normalized = market == null || market.isBlank()
                ? "domestic"
                : market.trim().toLowerCase(Locale.ROOT);

        if ("domestic".equals(normalized)) {
            return List.of("KOSPI", "KOSDAQ");
        }
        if ("overseas".equals(normalized)) {
            return List.of("NASDAQ", "NYSE", "AMEX");
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "market must be domestic or overseas");
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "stockCode is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
