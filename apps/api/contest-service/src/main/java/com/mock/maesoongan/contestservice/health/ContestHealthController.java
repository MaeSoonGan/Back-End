package com.mock.maesoongan.contestservice.health;

import com.mock.maesoongan.contestservice.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/health")
public class ContestHealthController {

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(new HealthResponse("UP", "contest-service", LocalDateTime.now()));
    }

    public record HealthResponse(String status, String service, LocalDateTime checkedAt) {
    }
}
