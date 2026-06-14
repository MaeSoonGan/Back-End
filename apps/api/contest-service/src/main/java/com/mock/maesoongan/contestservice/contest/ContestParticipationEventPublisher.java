package com.mock.maesoongan.contestservice.contest;

import com.mock.maesoongan.contestservice.common.BusinessException;
import com.mock.maesoongan.contestservice.contest.ContestParticipationEvents.ContestParticipationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class ContestParticipationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContestParticipationEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String contestParticipationEventTopic;
    private final Duration sendTimeout;
    private final boolean kafkaEnabled;

    public ContestParticipationEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.contest-participation-event}") String contestParticipationEventTopic,
            @Value("${app.kafka.send-timeout}") Duration sendTimeout,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.contestParticipationEventTopic = contestParticipationEventTopic;
        this.sendTimeout = sendTimeout;
        this.kafkaEnabled = kafkaEnabled;
    }

    public void publish(ContestParticipationEvent event) {
        String key = event.contestId() + ":" + event.memberId();
        if (!kafkaEnabled) {
            log.warn("Kafka disabled. Skip publish. topic={}, key={}, eventType={}",
                    contestParticipationEventTopic, key, event.eventType());
            return;
        }
        try {
            kafkaTemplate.send(contestParticipationEventTopic, key, event)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            log.error("Failed to publish contest participation event. topic={}, key={}, eventType={}",
                    contestParticipationEventTopic, key, event.eventType(), exception);
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "KAFKA_PUBLISH_FAILED",
                    "Failed to publish contest participation event."
            );
        }
    }
}
