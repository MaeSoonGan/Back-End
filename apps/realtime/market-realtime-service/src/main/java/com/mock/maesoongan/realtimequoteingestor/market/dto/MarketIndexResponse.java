package com.mock.maesoongan.realtimequoteingestor.market.dto;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketIndexResponse(
        String market,
        BigDecimal value,
        BigDecimal change,
        BigDecimal changeRate,
        long volume,
        LocalDateTime timestamp
) {

    public static MarketIndexResponse from(IndexQuoteEvent event) {
        return new MarketIndexResponse(
                event.name(),
                event.value(),
                event.change(),
                event.changeRate(),
                event.volume(),
                event.sourceTimestamp()
        );
    }
}
