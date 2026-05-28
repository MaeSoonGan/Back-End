package com.mock.maesoongan.orderservice.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotNull(message = "stockId is required")
            Long stockId,

            Long contestId,

            @NotBlank(message = "stockCode is required")
            String stockCode,

            @NotBlank(message = "side is required")
            String side,

            @NotBlank(message = "orderType is required")
            String orderType,

            @Positive(message = "price must be greater than 0")
            BigDecimal price,

            @Min(value = 1, message = "quantity must be greater than 0")
            long quantity
    ) {
    }

    public record CreateOrderResponse(
            long orderId,
            String status,
            String message,
            LocalDateTime acceptedAt
    ) {
    }

    public record CancelOrderResponse(
            long orderId,
            String status,
            String message,
            LocalDateTime requestedAt
    ) {
    }

    public record OrderListResponse(
            List<OrderItem> content,
            int page,
            int size,
            int totalElements,
            boolean hasNext
    ) {
    }

    public record OrderItem(
            long orderId,
            long contestId,
            String stockCode,
            String stockName,
            String side,
            String orderType,
            long quantity,
            long remainingQuantity,
            BigDecimal price,
            String status,
            String orderNumber,
            LocalDateTime createdAt
    ) {
    }

    public record TradeListResponse(
            TradeSummary summary,
            List<TradeItem> content,
            int page,
            int size,
            int totalElements,
            boolean hasNext
    ) {
    }

    public record TradeSummary(
            long buyQuantity,
            long sellQuantity,
            BigDecimal totalAmount
    ) {
    }

    public record TradeItem(
            long tradeId,
            long orderId,
            long contestId,
            String stockCode,
            String stockName,
            String side,
            long quantity,
            BigDecimal price,
            BigDecimal totalAmount,
            BigDecimal fee,
            BigDecimal tax,
            BigDecimal netAmount,
            LocalDateTime executedAt
    ) {
    }
}
