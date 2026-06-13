package com.mock.maesoongan.authservice.onprem;

import java.time.LocalDateTime;

public record OnPremCommandData(
        String requestId,
        String commandType,
        String status,
        String reason,
        LocalDateTime processedAt
) {
}
