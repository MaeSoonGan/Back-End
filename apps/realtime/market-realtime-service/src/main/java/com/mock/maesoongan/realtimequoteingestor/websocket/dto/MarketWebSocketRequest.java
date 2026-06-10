package com.mock.maesoongan.realtimequoteingestor.websocket.dto;

import java.util.List;

public record MarketWebSocketRequest(
        MarketWebSocketAction action,
        List<String> stockCodes,
        List<String> markets
) {
}
