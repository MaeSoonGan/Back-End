package com.mock.maesoongan.userrealtime.health;

import com.mock.maesoongan.userrealtime.common.ApiResponse;
import com.mock.maesoongan.userrealtime.realtime.RealtimeSessionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping
public class RealtimeHealthController {

    private final RealtimeSessionManager sessionManager;

    public RealtimeHealthController(RealtimeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @GetMapping({"/api/health", "/api/realtime/health"})
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(new HealthResponse(
                "UP",
                "user-realtime-service",
                sessionManager.totalConnectionCount(),
                LocalDateTime.now()
        ));
    }

    public record HealthResponse(
            String status,
            String service,
            int activeConnections,
            LocalDateTime checkedAt
    ) {
    }
}
