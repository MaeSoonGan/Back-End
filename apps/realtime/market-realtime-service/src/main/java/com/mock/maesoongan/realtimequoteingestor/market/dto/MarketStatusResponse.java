package com.mock.maesoongan.realtimequoteingestor.market.dto;

import java.time.LocalTime;

public record MarketStatusResponse(
        MarketStatusType marketStatus,
        String statusDescription,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime currentTime,
        boolean isOrderable
) {
}
