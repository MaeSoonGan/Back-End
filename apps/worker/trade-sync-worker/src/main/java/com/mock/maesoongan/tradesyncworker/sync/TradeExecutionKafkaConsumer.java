package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        TradeSyncRequest request = parse(payload);
        SyncResult result = tradeSyncService.syncTrade(request);
        log.info(
                "Consumed execution event. eventId={}, tradeId={}, orderId={}, status={}",
                result.eventId(),
                request.tradeId(),
                request.orderId(),
                result.processStatus()
        );
    }

    private TradeSyncRequest parse(String payload) {
        try {
            return objectMapper.readValue(payload, TradeSyncRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid execution.confirmed event payload", exception);
        }
    }
}
