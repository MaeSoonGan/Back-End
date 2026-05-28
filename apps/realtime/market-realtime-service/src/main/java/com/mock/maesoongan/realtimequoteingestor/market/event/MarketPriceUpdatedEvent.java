package com.mock.maesoongan.realtimequoteingestor.market.event;

import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;

public record MarketPriceUpdatedEvent(MarketPriceResponse price) {
}
