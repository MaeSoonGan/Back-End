package com.mock.maesoongan.orderservice.order;

import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderCancelRequestedEvent;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String orderRequestTopic;
    private final String orderCancelTopic;
    private final Duration sendTimeout;
    private final boolean kafkaEnabled;

    public OrderEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.order-request}") String orderRequestTopic,
            @Value("${app.kafka.topic.order-cancel}") String orderCancelTopic,
            @Value("${app.kafka.send-timeout}") Duration sendTimeout,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRequestTopic = orderRequestTopic;
        this.orderCancelTopic = orderCancelTopic;
        this.sendTimeout = sendTimeout;
        this.kafkaEnabled = kafkaEnabled;
    }

    public void publishOrderRequested(OrderRequestedEvent event) {
        send(orderRequestTopic, String.valueOf(event.orderId()), event);
    }

    public void publishOrderCancelRequested(OrderCancelRequestedEvent event) {
        send(orderCancelTopic, String.valueOf(event.orderId()), event);
    }

    private void send(String topic, String key, Object event) {
        if (!kafkaEnabled) {
            log.warn("Kafka disabled. Skip publish. topic={}, key={}", topic, key);
            return;
        }
        try {
            kafkaTemplate.send(topic, key, event).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "KAFKA_PUBLISH_FAILED", "Failed to publish order event.");
        }
    }
}
