package com.mock.maesoongan.realtimequoteingestor.market.event;

import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketOrderbookResponse;

public record MarketOrderbookUpdatedEvent(MarketOrderbookResponse orderbook) {
}
