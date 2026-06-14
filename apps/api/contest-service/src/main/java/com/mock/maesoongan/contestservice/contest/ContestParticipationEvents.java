package com.mock.maesoongan.contestservice.contest;

import java.time.LocalDateTime;

public final class ContestParticipationEvents {

    private ContestParticipationEvents() {
    }

    public record ContestParticipationEvent(
            String eventType,
            String requestId,
            String status,
            long memberId,
            String userId,
            long contestId,
            LocalDateTime confirmedAt
    ) {
    }
}
