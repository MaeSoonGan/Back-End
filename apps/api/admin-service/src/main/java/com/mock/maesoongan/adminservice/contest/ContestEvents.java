package com.mock.maesoongan.adminservice.contest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ContestEvents {

    private ContestEvents() {
    }

    public record ContestEvent(
            String eventType,
            long contestId,
            String title,
            String status,
            BigDecimal initialCash,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
