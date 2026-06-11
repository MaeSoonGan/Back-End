package com.mock.maesoongan.notificationapi.notification.controller;

import com.mock.maesoongan.notificationapi.common.ApiResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.CreateNotificationRequest;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.CreateNotificationResponse;
import com.mock.maesoongan.notificationapi.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스(order/contest 등)가 이벤트 발생 시 알림을 생성하기 위해 호출하는 내부용 API.
 * 회원의 수신 설정(notification_setting)을 확인해 허용된 종류만 저장한다.
 */
@RestController
@RequestMapping("/api/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;

    public InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "Create notification (internal, setting-gated)")
    @PostMapping
    public ApiResponse<CreateNotificationResponse> create(@RequestBody CreateNotificationRequest request) {
        return ApiResponse.success(notificationService.createNotification(request));
    }
}
