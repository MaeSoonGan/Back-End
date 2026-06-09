package com.mock.maesoongan.realtimequoteingestor.market.dto;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;

import java.time.LocalDateTime;
import java.util.List;

public record MarketOrderbookResponse(
        String stockCode,
        String stockName,
        List<OrderbookLevel> asks,
        List<OrderbookLevel> bids,
        LocalDateTime timestamp
) {

    public static MarketOrderbookResponse from(OrderbookQuoteEvent event) {
        return new MarketOrderbookResponse(
                event.code(),
                event.name(),
                event.asks(),
                event.bids(),
                event.sourceTimestamp()
        );
    }
}
