package com.mock.maesoongan.orderservice.kis;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class KisDtos {

    private KisDtos() {
    }

    public record KisOrderRequest(
            @NotBlank(message = "stockCode is required")
            String stockCode,

            @NotBlank(message = "side is required")
            String side,

            @NotBlank(message = "orderType is required")
            String orderType,

            @Min(value = 1, message = "quantity must be greater than 0")
            long quantity,

            @PositiveOrZero(message = "price must be greater than or equal to 0")
            BigDecimal price
    ) {
    }

    public record KisOrderResponse(
            String resultCode,
            String messageCode,
            String message,
            JsonNode output,
            LocalDateTime requestedAt
    ) {
    }
}
