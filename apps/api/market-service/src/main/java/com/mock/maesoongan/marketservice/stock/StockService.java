package com.mock.maesoongan.marketservice.stock;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.OrderbookLevelRow;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockDailyPriceRow;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockPriceRow;
import com.mock.maesoongan.marketservice.stock.StockDtos.OrderbookLevel;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockDailyInfoResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockOrderbookResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockPriceResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockSearchItem;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class StockService {

    private final MarketDataRepository marketDataRepository;

    public StockService(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    @Transactional(readOnly = true)
    public StockPriceResponse getPrice(String code) {
        StockPriceRow row = marketDataRepository.findStockPrice(normalizeCode(code))
                .orElseThrow(() -> notFound("Stock not found"));

        return new StockPriceResponse(
                row.stockId(),
                row.code(),
                row.name(),
                row.currentPrice(),
                row.changeAmount(),
                row.changeRate(),
                row.volume()
        );
    }

    @Transactional(readOnly = true)
    public StockDailyInfoResponse getDailyInfo(String code) {
        StockDailyPriceRow row = marketDataRepository.findLatestDailyPrice(normalizeCode(code))
                .orElseThrow(() -> notFound("Stock daily price not found"));

        return new StockDailyInfoResponse(
                row.openPrice(),
                row.highPrice(),
                row.lowPrice(),
                row.prevClosePrice()
        );
    }

    @Transactional(readOnly = true)
    public StockOrderbookResponse getOrderbook(String code) {
        String normalizedCode = normalizeCode(code);
        if (!marketDataRepository.existsActiveStock(normalizedCode)) {
            throw notFound("Stock not found");
        }

        return new StockOrderbookResponse(
                toOrderbookLevels(marketDataRepository.findOrderbookLevels(normalizedCode, "ASK")),
                toOrderbookLevels(marketDataRepository.findOrderbookLevels(normalizedCode, "BID"))
        );
    }

    @Transactional(readOnly = true)
    public StockSearchResponse search(String keyword, String market, Long memberId) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedMarket = normalizeStockMarket(market);

        List<StockSearchItem> stocks = marketDataRepository.searchStocks(normalizedKeyword, normalizedMarket, memberId)
                .stream()
                .map(row -> new StockSearchItem(
                        row.stockId(),
                        row.stockCode(),
                        row.stockName(),
                        row.market(),
                        row.currentPrice(),
                        row.changeRate(),
                        null,
                        row.watchlisted()
                ))
                .toList();

        return new StockSearchResponse(stocks);
    }

    private List<OrderbookLevel> toOrderbookLevels(List<OrderbookLevelRow> rows) {
        return rows.stream()
                .map(row -> new OrderbookLevel(row.price(), row.quantity()))
                .toList();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw badRequest("stock code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw badRequest("keyword is required");
        }
        return keyword.trim();
    }

    private String normalizeStockMarket(String market) {
        if (market == null || market.isBlank()) {
            throw badRequest("market is required");
        }

        String normalized = market.trim().toUpperCase(Locale.ROOT);
        if (!"KOSPI".equals(normalized) && !"KOSDAQ".equals(normalized)) {
            throw badRequest("market must be KOSPI or KOSDAQ");
        }
        return normalized;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
