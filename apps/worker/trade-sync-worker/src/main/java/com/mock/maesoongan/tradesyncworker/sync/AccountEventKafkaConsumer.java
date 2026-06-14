package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.AccountEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class AccountEventKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final TradeSyncService tradeSyncService;

    public AccountEventKafkaConsumer(ObjectMapper objectMapper, TradeSyncService tradeSyncService) {
        this.objectMapper = objectMapper;
        this.tradeSyncService = tradeSyncService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.account-event}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeAccountEvent(String payload) {
        AccountEvent event = parse(payload);
        SyncResult result = tradeSyncService.syncAccountEvent(event);
        log.info(
                "Consumed account event. eventId={}, requestId={}, eventType={}, status={}, accountId={}, processStatus={}",
                result.eventId(),
                event.requestId(),
                event.eventType(),
                event.status(),
                event.accountId(),
                result.processStatus()
        );
    }

    private AccountEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, AccountEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid account.event payload", exception);
        }
    }
}
