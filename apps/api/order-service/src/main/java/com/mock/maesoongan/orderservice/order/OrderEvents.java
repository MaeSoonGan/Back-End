package com.mock.maesoongan.orderservice.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class OrderEvents {

    private OrderEvents() {
    }

    public record OrderRequestedEvent(
            String eventId,
            long orderId,
            String orderNumber,
            long memberId,
            long contestId,
            long stockId,
            String stockCode,
            String side,
            String orderType,
            BigDecimal orderPrice,
            long orderQuantity,
            BigDecimal reservedAmount,
            LocalDateTime requestedAt
    ) {
    }

    public record OrderCancelRequestedEvent(
            String eventId,
            long orderId,
            String orderNumber,
            long memberId,
            long contestId,
            String stockCode,
            String side,
            BigDecimal orderPrice,
            long remainingQuantity,
            BigDecimal pendingReleaseAmount,
            LocalDateTime requestedAt
    ) {
    }
}
