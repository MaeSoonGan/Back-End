package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.ExecutionConfirmedEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.TradeSyncRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class TradeExecutionKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutionKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final TradeSyncService tradeSyncService;

    public TradeExecutionKafkaConsumer(ObjectMapper objectMapper, TradeSyncService tradeSyncService) {
        this.objectMapper = objectMapper;
        this.tradeSyncService = tradeSyncService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.execution-confirmed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeExecutionConfirmed(String payload) {
        JsonNode node = parseTree(payload);
        if (isOnPremExecutionConfirmed(node)) {
            ExecutionConfirmedEvent event = parse(node, ExecutionConfirmedEvent.class);
            SyncResult result = tradeSyncService.syncExecutionConfirmed(event);
            log.info(
                    "Consumed on-prem execution.confirmed event. executionId={}, orderId={}, status={}",
                    event.executionId(),
                    event.orderId(),
                    result.processStatus()
            );
            return;
        }

        TradeSyncRequest request = parse(node, TradeSyncRequest.class);
        SyncResult result = tradeSyncService.syncTrade(request);
        log.info(
                "Consumed execution event. eventId={}, tradeId={}, orderId={}, status={}",
                result.eventId(),
                request.tradeId(),
                request.orderId(),
                result.processStatus()
        );
    }

    private JsonNode parseTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid execution.confirmed event payload", exception);
        }
    }

    private boolean isOnPremExecutionConfirmed(JsonNode node) {
        return node.hasNonNull("accountId") || node.hasNonNull("confirmedAt");
    }

    private <T> T parse(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid execution.confirmed event payload", exception);
        }
    }
}
