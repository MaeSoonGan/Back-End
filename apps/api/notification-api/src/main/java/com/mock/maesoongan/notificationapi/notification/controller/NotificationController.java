package com.mock.maesoongan.notificationapi.notification.controller;

import com.mock.maesoongan.notificationapi.auth.CurrentMemberProvider;
import com.mock.maesoongan.notificationapi.common.ApiResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationListResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationSettingsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadAllNotificationsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadNotificationResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UnreadCountResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UpdateNotificationSettingsRequest;
import com.mock.maesoongan.notificationapi.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "Notification API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentMemberProvider currentMemberProvider;

    public NotificationController(NotificationService notificationService, CurrentMemberProvider currentMemberProvider) {
        this.notificationService = notificationService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @Operation(summary = "Get notification list")
    @GetMapping
    public ApiResponse<NotificationListResponse> getNotifications() {
        return ApiResponse.success(notificationService.getNotifications(currentMemberProvider.memberId()));
    }

    @Operation(summary = "Mark one notification as read")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<ReadNotificationResponse> markAsRead(@PathVariable Long notificationId) {
        return ApiResponse.success(notificationService.markAsRead(currentMemberProvider.memberId(), notificationId));
    }

    @Operation(summary = "Mark all notifications as read")
    @PatchMapping("/read-all")
    public ApiResponse<ReadAllNotificationsResponse> markAllAsRead() {
        return ApiResponse.success(notificationService.markAllAsRead(currentMemberProvider.memberId()));
    }

    @Operation(summary = "Get unread notification count")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount() {
        return ApiResponse.success(notificationService.getUnreadCount(currentMemberProvider.memberId()));
    }

    @Operation(summary = "Get notification settings")
    @GetMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> getSettings() {
        return ApiResponse.success(notificationService.getSettings(currentMemberProvider.memberId()));
    }

    @Operation(summary = "Update notification settings")
    @PatchMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> updateSettings(
            @RequestBody UpdateNotificationSettingsRequest request
    ) {
        return ApiResponse.success(notificationService.updateSettings(currentMemberProvider.memberId(), request));
    }
}
