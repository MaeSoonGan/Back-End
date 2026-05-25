package com.mock.maesoongan.notificationapi.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationListResponse(
            long unreadCount,
            List<NotificationItem> items
    ) {
    }

    public record NotificationItem(
            Long notificationId,
            String type,
            String title,
            String body,
            boolean isRead,
            LocalDateTime createdAt
    ) {
    }

    public record UnreadCountResponse(
            long unreadCount
    ) {
    }

    public record NotificationSettingsResponse(
            boolean tradeComplete,
            boolean orderCancel,
            boolean pendingOrder,
            boolean contestStart,
            boolean contestEnd,
            boolean rankChange,
            boolean marketOpen,
            boolean marketClose
    ) {
    }

    public record UpdateNotificationSettingsRequest(
            Boolean tradeComplete,
            Boolean orderCancel,
            Boolean pendingOrder,
            Boolean contestStart,
            Boolean contestEnd,
            Boolean rankChange,
            Boolean marketOpen,
            Boolean marketClose
    ) {
    }

    public record ReadNotificationResponse(
            Long notificationId,
            boolean isRead
    ) {
    }

    public record ReadAllNotificationsResponse(
            int updatedCount
    ) {
    }
}
