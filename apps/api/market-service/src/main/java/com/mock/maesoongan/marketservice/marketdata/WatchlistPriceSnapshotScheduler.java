package com.mock.maesoongan.marketservice.marketdata;

import com.mock.maesoongan.marketservice.marketdata.kis.KisCurrentPriceClient;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 관심종목에 등록된 종목들의 현재가를 KIS REST로 받아 stock_price_snapshot(읽기 모델)에 적재한다.
 * - 관심종목 화면이 ws 없이 이 스냅샷(REST)만으로 가격을 표시 → ws 등록 한도 절약
 * - REST 호출이라 ws 구독 한도와 무관
 */
@Component
public class WatchlistPriceSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(WatchlistPriceSnapshotScheduler.class);
    private static final int MAX_CODES = 50;

    private final MarketDataRepository marketDataRepository;
    private final KisCurrentPriceClient kisCurrentPriceClient;

    public WatchlistPriceSnapshotScheduler(
            MarketDataRepository marketDataRepository,
            KisCurrentPriceClient kisCurrentPriceClient
    ) {
        this.marketDataRepository = marketDataRepository;
        this.kisCurrentPriceClient = kisCurrentPriceClient;
    }

    // 평일 09~15시 KST, 30초마다 (종목당 KIS REST 1회 → REST rate 고려해 30초)
    @Scheduled(cron = "0/30 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void captureWatchlistPrices() {
        try {
            List<String> codes = marketDataRepository.findWatchlistedStockCodes(MAX_CODES);
            if (codes.isEmpty()) {
                return;
            }
            int updated = 0;
            for (String code : codes) {
                var price = kisCurrentPriceClient.fetchPrice(code);
                if (price.isPresent() && price.get().price().signum() > 0) {
                    marketDataRepository.upsertStockPriceSnapshot(
                            code,
                            price.get().price(),
                            price.get().change(),
                            price.get().changeRate(),
                            price.get().volume()
                    );
                    updated++;
                }
            }
            log.info("watchlist price snapshot updated: total={}, updated={}", codes.size(), updated);
        } catch (RuntimeException exception) {
            log.warn("watchlist price snapshot capture failed: {}", exception.getMessage());
        }
    }
}
