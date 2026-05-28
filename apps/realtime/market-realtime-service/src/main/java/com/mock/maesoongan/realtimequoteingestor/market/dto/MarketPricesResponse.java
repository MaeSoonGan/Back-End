package com.mock.maesoongan.realtimequoteingestor.market.dto;

import java.util.List;

public record MarketPricesResponse(
        List<MarketPriceSummary> prices,
        List<String> notFound
) {
}
