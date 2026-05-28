package com.mock.maesoongan.userrealtime.realtime;

import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.NotificationEvent;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.NotificationEventRequest;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.OrderEvent;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.OrderEventRequest;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.PublishResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class RealtimeEventPublisher {

    private final RealtimeSessionManager sessionManager;

    public RealtimeEventPublisher(RealtimeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public PublishResponse publishOrderEvent(OrderEventRequest request) {
        OrderEvent event = new OrderEvent(
                request.memberId(),
                request.orderId(),
                request.contestId(),
                normalizeStatus(request.status()),
                request.stockCode(),
                request.stockName(),
                request.executedQuantity(),
                request.executedPrice(),
                resolveOrderMessage(request.status(), request.message()),
                LocalDateTime.now()
        );
        String eventName = orderEventName(event.status());
        int deliveredCount = sessionManager.publish(event.memberId(), eventName, event);
        return new PublishResponse(event.memberId(), eventName, deliveredCount, "Order event published");
    }

    public PublishResponse publishNotificationEvent(NotificationEventRequest request) {
        NotificationEvent event = new NotificationEvent(
                request.memberId(),
                request.notificationId(),
                request.type(),
                request.title(),
                request.body(),
                LocalDateTime.now()
        );
        int deliveredCount = sessionManager.publish(
                event.memberId(),
                RealtimeEventType.NOTIFICATION_CREATED.name(),
                event
        );
        return new PublishResponse(
                event.memberId(),
                RealtimeEventType.NOTIFICATION_CREATED.name(),
                deliveredCount,
                "Notification event published"
        );
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private String orderEventName(String status) {
        if ("FILLED".equals(status) || "PARTIALLY_FILLED".equals(status)) {
            return RealtimeEventType.ORDER_FILLED.name();
        }
        if ("CANCELED".equals(status)) {
            return RealtimeEventType.ORDER_CANCELED.name();
        }
        return RealtimeEventType.ORDER_STATUS_CHANGED.name();
    }

    private String resolveOrderMessage(String status, String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }

        String normalizedStatus = normalizeStatus(status);
        if ("FILLED".equals(normalizedStatus)) {
            return "주문이 체결되었습니다.";
        }
        if ("PARTIALLY_FILLED".equals(normalizedStatus)) {
            return "주문이 일부 체결되었습니다.";
        }
        if ("CANCELED".equals(normalizedStatus)) {
            return "주문이 취소되었습니다.";
        }
        return "주문 상태가 변경되었습니다.";
    }
}
