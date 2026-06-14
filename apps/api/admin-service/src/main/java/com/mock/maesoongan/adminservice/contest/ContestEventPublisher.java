package com.mock.maesoongan.adminservice.contest;

import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.contest.ContestEvents.ContestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class ContestEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContestEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String contestEventTopic;
    private final Duration sendTimeout;
    private final boolean kafkaEnabled;

    public ContestEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.contest-event}") String contestEventTopic,
            @Value("${app.kafka.send-timeout}") Duration sendTimeout,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.contestEventTopic = contestEventTopic;
        this.sendTimeout = sendTimeout;
        this.kafkaEnabled = kafkaEnabled;
    }

    public void publish(ContestEvent event) {
        if (!kafkaEnabled) {
            log.warn("Kafka disabled. Skip publish. topic={}, key={}, eventType={}",
                    contestEventTopic, event.contestId(), event.eventType());
            return;
        }
        try {
            kafkaTemplate.send(contestEventTopic, String.valueOf(event.contestId()), event)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            log.error("Failed to publish contest event. topic={}, key={}, eventType={}",
                    contestEventTopic, event.contestId(), event.eventType(), exception);
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "KAFKA_PUBLISH_FAILED",
                    "Failed to publish contest event."
            );
        }
    }
}
