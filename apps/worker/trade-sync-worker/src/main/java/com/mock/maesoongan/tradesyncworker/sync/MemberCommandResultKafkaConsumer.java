package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.MemberCommandResultEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class MemberCommandResultKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemberCommandResultKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final TradeSyncService tradeSyncService;

    public MemberCommandResultKafkaConsumer(ObjectMapper objectMapper, TradeSyncService tradeSyncService) {
        this.objectMapper = objectMapper;
        this.tradeSyncService = tradeSyncService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.member-command-result}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeMemberCommandResult(String payload) {
        MemberCommandResultEvent event = parse(payload);
        SyncResult result = tradeSyncService.syncMemberCommandResult(event);
        log.info(
                "Consumed member command result. eventId={}, requestId={}, eventType={}, status={}, processStatus={}",
                result.eventId(),
                event.requestId(),
                event.eventType(),
                event.status(),
                result.processStatus()
        );
    }

    private MemberCommandResultEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, MemberCommandResultEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid member.command.result event payload", exception);
        }
    }
}
