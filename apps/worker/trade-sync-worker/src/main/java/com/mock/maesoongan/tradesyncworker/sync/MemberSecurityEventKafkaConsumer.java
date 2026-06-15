package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.MemberSecurityEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 온프렘 member.security.event(로그인 자동정지/관리자정지/해제/명령거부)를 받아
 * member_snapshot의 status·login_fail_count를 미러링한다. (record key = memberId → 회원 단위 순서 보장)
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class MemberSecurityEventKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemberSecurityEventKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final TradeSyncService tradeSyncService;

    public MemberSecurityEventKafkaConsumer(ObjectMapper objectMapper, TradeSyncService tradeSyncService) {
        this.objectMapper = objectMapper;
        this.tradeSyncService = tradeSyncService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.member-security-event}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeMemberSecurityEvent(String payload) {
        MemberSecurityEvent event = parse(payload);
        SyncResult result = tradeSyncService.syncMemberSecurityEvent(event);
        log.info(
                "Consumed member security event. eventId={}, eventType={}, memberId={}, status={}, loginFailCount={}, processStatus={}",
                result.eventId(),
                event.eventType(),
                event.memberId(),
                event.status(),
                event.loginFailCount(),
                result.processStatus()
        );
    }

    private MemberSecurityEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, MemberSecurityEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid member.security.event payload", exception);
        }
    }
}
