package com.mock.maesoongan.userrealtime.realtime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class RealtimeDtos {

    private RealtimeDtos() {
    }

    @Schema(description = "Realtime stream connected event")
    public record StreamConnectedEvent(
            long memberId,
            String message,
            LocalDateTime connectedAt
    ) {
    }

    @Schema(description = "Realtime heartbeat event")
    public record HeartbeatEvent(
            long memberId,
            LocalDateTime sentAt
    ) {
    }

    @Schema(description = "Order realtime test event request")
    public record OrderEventRequest(
            @NotNull(message = "memberId is required")
            Long memberId,

            @NotNull(message = "orderId is required")
            Long orderId,

            Long contestId,

            @NotBlank(message = "status is required")
            String status,

            String stockCode,
            String stockName,
            Integer executedQuantity,
            BigDecimal executedPrice,
            String message
    ) {
    }

    @Schema(description = "Order realtime event")
    public record OrderEvent(
            long memberId,
            long orderId,
            Long contestId,
            String status,
            String stockCode,
            String stockName,
            Integer executedQuantity,
            BigDecimal executedPrice,
            String message,
            LocalDateTime occurredAt
    ) {
    }

    @Schema(description = "Notification realtime test event request")
    public record NotificationEventRequest(
            @NotNull(message = "memberId is required")
            Long memberId,

            Long notificationId,

            @NotBlank(message = "type is required")
            String type,

            @NotBlank(message = "title is required")
            String title,

            @NotBlank(message = "body is required")
            String body
    ) {
    }

    @Schema(description = "Notification realtime event")
    public record NotificationEvent(
            long memberId,
            Long notificationId,
            String type,
            String title,
            String body,
            LocalDateTime occurredAt
    ) {
    }

    @Schema(description = "Realtime publish response")
    public record PublishResponse(
            long memberId,
            String eventName,
            int deliveredCount,
            String message
    ) {
    }
}
