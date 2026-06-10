package com.mock.maesoongan.realtimequoteingestor.websocket.dto;

public record MarketWebSocketMessage<T>(
        String type,
        T data
) {

    public static <T> MarketWebSocketMessage<T> priceUpdate(T data) {
        return new MarketWebSocketMessage<>("PRICE_UPDATE", data);
    }

    public static <T> MarketWebSocketMessage<T> orderbookUpdate(T data) {
        return new MarketWebSocketMessage<>("ORDERBOOK_UPDATE", data);
    }

    public static <T> MarketWebSocketMessage<T> indexUpdate(T data) {
        return new MarketWebSocketMessage<>("INDEX_UPDATE", data);
    }

    public static <T> MarketWebSocketMessage<T> marketStatus(T data) {
        return new MarketWebSocketMessage<>("MARKET_STATUS", data);
    }
}
