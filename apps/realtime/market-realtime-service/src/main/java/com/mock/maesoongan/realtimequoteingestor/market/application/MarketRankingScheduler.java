package com.mock.maesoongan.realtimequoteingestor.market.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ranking.hts-top-view", name = "enabled", havingValue = "true")
public class MarketRankingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketRankingScheduler.class);

    private final MarketRankingService marketRankingService;
    private final MarketIndexQuoteService marketIndexQuoteService;

    public MarketRankingScheduler(
            MarketRankingService marketRankingService,
            MarketIndexQuoteService marketIndexQuoteService
    ) {
        this.marketRankingService = marketRankingService;
        this.marketIndexQuoteService = marketIndexQuoteService;
    }

    @Scheduled(
            initialDelayString = "${ranking.hts-top-view.initial-delay-ms:1000}",
            fixedDelayString = "${ranking.hts-top-view.poll-interval-ms:10000}"
    )
    public void refreshHtsTopViewRanking() {
        try {
            marketRankingService.refreshHtsTopViewRanking();
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh KIS HTS top view ranking cache", exception);
        }
    }

    @Scheduled(
            initialDelayString = "${ranking.hts-top-view.initial-delay-ms:1000}",
            fixedDelayString = "${ranking.hts-top-view.poll-interval-ms:10000}"
    )
    public void refreshIndices() {
        try {
            marketIndexQuoteService.refreshIndices();
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh KIS index quote cache", exception);
        }
    }
}
