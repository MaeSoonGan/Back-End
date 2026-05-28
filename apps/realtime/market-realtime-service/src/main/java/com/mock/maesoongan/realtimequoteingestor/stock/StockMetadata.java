package com.mock.maesoongan.realtimequoteingestor.stock;

public record StockMetadata(
        String code,
        String standardCode,
        String name,
        String market
) {
}
