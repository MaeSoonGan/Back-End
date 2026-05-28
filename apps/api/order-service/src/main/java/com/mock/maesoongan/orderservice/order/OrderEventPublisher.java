package com.mock.maesoongan.orderservice.order;

import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderCancelRequestedEvent;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderRequestedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String orderRequestedTopic;
    private final String orderCancelRequestedTopic;
    private final Duration sendTimeout;

    public OrderEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.order-requested}") String orderRequestedTopic,
            @Value("${app.kafka.topic.order-cancel-requested}") String orderCancelRequestedTopic,
            @Value("${app.kafka.send-timeout}") Duration sendTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRequestedTopic = orderRequestedTopic;
        this.orderCancelRequestedTopic = orderCancelRequestedTopic;
        this.sendTimeout = sendTimeout;
    }

    public void publishOrderRequested(OrderRequestedEvent event) {
        send(orderRequestedTopic, String.valueOf(event.orderId()), event);
    }

    public void publishOrderCancelRequested(OrderCancelRequestedEvent event) {
        send(orderCancelRequestedTopic, String.valueOf(event.orderId()), event);
    }

    private void send(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "KAFKA_PUBLISH_FAILED", "주문 처리 중 오류가 발생했습니다.");
        }
    }
}
