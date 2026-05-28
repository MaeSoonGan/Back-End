package com.mock.maesoongan.userrealtime.realtime;

import com.mock.maesoongan.userrealtime.common.ApiResponse;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.NotificationEventRequest;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.OrderEventRequest;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.PublishResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal Realtime Test Events", description = "Internal realtime test event publisher")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/internal/realtime/test-events")
public class InternalRealtimeTestController {

    private final RealtimeEventPublisher eventPublisher;

    public InternalRealtimeTestController(RealtimeEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Operation(summary = "Publish order realtime test event")
    @PostMapping("/order")
    public ApiResponse<PublishResponse> publishOrderEvent(@Valid @RequestBody OrderEventRequest request) {
        return ApiResponse.success(eventPublisher.publishOrderEvent(request));
    }

    @Operation(summary = "Publish notification realtime test event")
    @PostMapping("/notification")
    public ApiResponse<PublishResponse> publishNotificationEvent(@Valid @RequestBody NotificationEventRequest request) {
        return ApiResponse.success(eventPublisher.publishNotificationEvent(request));
    }
}
