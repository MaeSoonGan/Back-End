package com.mock.maesoongan.realtimequoteingestor.market.event;

import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;

public record MarketIndexUpdatedEvent(MarketIndexResponse index) {
}
