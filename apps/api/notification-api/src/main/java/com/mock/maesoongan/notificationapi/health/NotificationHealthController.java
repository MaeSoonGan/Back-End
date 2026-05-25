package com.mock.maesoongan.notificationapi.health;

import com.mock.maesoongan.notificationapi.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class NotificationHealthController {

    private final JdbcTemplate jdbcTemplate;

    public NotificationHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public ApiResponse<HealthResponse> health() {
        Integer database = jdbcTemplate.queryForObject("select 1", Integer.class);
        return ApiResponse.success(new HealthResponse("notification-api", "UP", database, LocalDateTime.now()));
    }

    public record HealthResponse(
            String service,
            String status,
            Integer database,
            LocalDateTime checkedAt
    ) {
    }
}
