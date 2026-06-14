package com.mock.maesoongan.adminservice.contest;

import com.mock.maesoongan.adminservice.contest.ContestEvents.ContestEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ContestEventPublisher publisher = new ContestEventPublisher(
            kafkaTemplate,
            "contest.event",
            Duration.ofSeconds(1),
            true
    );

    @Test
    void publishUsesContestEventTopicKeyAndPayload() {
        ContestEvent event = new ContestEvent(
                "CONTEST_CREATED",
                10L,
                "June Contest",
                "ACTIVE",
                new BigDecimal("1000000"),
                LocalDateTime.of(2026, 6, 14, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                LocalDateTime.of(2026, 6, 14, 8, 30),
                LocalDateTime.of(2026, 6, 14, 8, 30)
        );
        when(kafkaTemplate.send(eq("contest.event"), eq("10"), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        verify(kafkaTemplate).send("contest.event", "10", event);
    }
}
