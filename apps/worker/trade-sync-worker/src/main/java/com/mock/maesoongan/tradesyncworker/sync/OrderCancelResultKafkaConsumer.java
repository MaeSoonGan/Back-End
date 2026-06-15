package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.OrderCancelResultEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OrderCancelResultKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelResultKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final TradeSyncService tradeSyncService;

    public OrderCancelResultKafkaConsumer(ObjectMapper objectMapper, TradeSyncService tradeSyncService) {
        this.objectMapper = objectMapper;
        this.tradeSyncService = tradeSyncService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.order-cancel-result}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrderCancelResult(String payload) {
        OrderCancelResultEvent event = parse(payload);
        SyncResult result = tradeSyncService.syncOrderCancelResult(event);
        log.info(
                "Consumed order cancel result. eventId={}, orderId={}, eventType={}, status={}, processStatus={}",
                event.eventId(),
                event.orderId(),
                event.eventType(),
                event.status(),
                result.processStatus()
        );
    }

    private OrderCancelResultEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, OrderCancelResultEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid order.cancel.result event payload", exception);
        }
    }
}
