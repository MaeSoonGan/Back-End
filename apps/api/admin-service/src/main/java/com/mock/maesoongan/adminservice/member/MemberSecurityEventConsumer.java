package com.mock.maesoongan.adminservice.member;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.adminservice.member.MemberSecurityDtos.MemberSecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 온프렘 member.security.event 수신 → 비정상탐지 알림 생애주기 + 관리자 명령 확정.
 * (member_snapshot의 status/login_fail_count 미러는 trade-sync-worker가 별도 consumer group으로 처리)
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class MemberSecurityEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemberSecurityEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final MemberSecurityService memberSecurityService;

    public MemberSecurityEventConsumer(ObjectMapper objectMapper, MemberSecurityService memberSecurityService) {
        this.objectMapper = objectMapper;
        this.memberSecurityService = memberSecurityService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.member-security-event}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String payload) {
        MemberSecurityEvent event = parse(payload);
        memberSecurityService.handleEvent(event);
        log.info("Consumed member security event. eventType={}, memberId={}, commandId={}, status={}",
                event.eventType(), event.memberId(), event.commandId(), event.status());
    }

    private MemberSecurityEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, MemberSecurityEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid member.security.event payload", exception);
        }
    }
}
