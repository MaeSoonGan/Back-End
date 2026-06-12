package com.mock.maesoongan.orderservice.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class OrderEvents {

    private OrderEvents() {
    }

    public record OrderRequestedEvent(
            long orderId,
            long accountId,
            String stockCode,
            String stockName,
            String orderType,
            String priceType,
            BigDecimal orderPrice,
            long orderQuantity,
            LocalDateTime requestedAt
    ) {
    }

    public record OrderCancelRequestedEvent(
            long orderId,
            long accountId,
            LocalDateTime requestedAt
    ) {
    }
}
