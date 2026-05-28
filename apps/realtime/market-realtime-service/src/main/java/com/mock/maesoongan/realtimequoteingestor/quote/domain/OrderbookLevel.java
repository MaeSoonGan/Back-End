package com.mock.maesoongan.realtimequoteingestor.quote.domain;

import java.math.BigDecimal;

public record OrderbookLevel(
        BigDecimal price,
        long quantity
) {

    public OrderbookLevel {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("price must be greater than or equal to 0");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be greater than or equal to 0");
        }
    }
}
