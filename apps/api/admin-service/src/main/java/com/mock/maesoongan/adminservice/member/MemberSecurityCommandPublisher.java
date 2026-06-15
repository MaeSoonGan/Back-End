package com.mock.maesoongan.adminservice.member;

import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.member.MemberSecurityDtos.MemberSecurityCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 관리자 정지/해제 명령을 온프렘으로 발행한다. Kafka record key = memberId (회원 단위 순서 보장).
 */
@Component
public class MemberSecurityCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(MemberSecurityCommandPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String commandTopic;
    private final Duration sendTimeout;
    private final boolean kafkaEnabled;

    public MemberSecurityCommandPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.member-security-command}") String commandTopic,
            @Value("${app.kafka.send-timeout}") Duration sendTimeout,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.commandTopic = commandTopic;
        this.sendTimeout = sendTimeout;
        this.kafkaEnabled = kafkaEnabled;
    }

    public void publish(MemberSecurityCommand command) {
        if (!kafkaEnabled) {
            log.warn("Kafka disabled. Skip member security command. topic={}, key={}, action={}",
                    commandTopic, command.memberId(), command.action());
            return;
        }
        try {
            kafkaTemplate.send(commandTopic, String.valueOf(command.memberId()), command)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            log.error("Failed to publish member security command. topic={}, key={}, action={}, commandId={}",
                    commandTopic, command.memberId(), command.action(), command.commandId(), exception);
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "KAFKA_PUBLISH_FAILED",
                    "Failed to publish member security command."
            );
        }
    }
}
