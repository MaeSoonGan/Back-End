package com.mock.maesoongan.contestservice.contest;

import com.mock.maesoongan.contestservice.contest.ContestParticipationEvents.ContestParticipationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestParticipationEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ContestParticipationEventPublisher publisher = new ContestParticipationEventPublisher(
            kafkaTemplate,
            "contest.participation.event",
            Duration.ofSeconds(1),
            true
    );

    @Test
    void publishUsesParticipationTopicKeyAndPayload() {
        ContestParticipationEvent event = new ContestParticipationEvent(
                "CONTEST_PARTICIPATION_CONFIRMED",
                "request-1",
                "CONFIRMED",
                56L,
                "testtest",
                10L,
                LocalDateTime.of(2026, 6, 14, 16, 1)
        );
        when(kafkaTemplate.send(eq("contest.participation.event"), eq("10:56"), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        verify(kafkaTemplate).send("contest.participation.event", "10:56", event);
    }
}
