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
    private final String orderRequestedTopic;
    private final String orderCancelRequestedTopic;
    private final Duration sendTimeout;
    // 로컬 개발 등 Kafka(온프렘) 접근 불가 환경에서 false로 두면 발행을 건너뜀(주문은 PENDING 생성).
    private final boolean kafkaEnabled;

    public OrderEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.order-requested}") String orderRequestedTopic,
            @Value("${app.kafka.topic.order-cancel-requested}") String orderCancelRequestedTopic,
            @Value("${app.kafka.send-timeout}") Duration sendTimeout,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRequestedTopic = orderRequestedTopic;
        this.orderCancelRequestedTopic = orderCancelRequestedTopic;
        this.sendTimeout = sendTimeout;
        this.kafkaEnabled = kafkaEnabled;
    }

    public void publishOrderRequested(OrderRequestedEvent event) {
        send(orderRequestedTopic, String.valueOf(event.orderId()), event);
    }

    public void publishOrderCancelRequested(OrderCancelRequestedEvent event) {
        send(orderCancelRequestedTopic, String.valueOf(event.orderId()), event);
    }

    private void send(String topic, String key, Object event) {
        if (!kafkaEnabled) {
            // 로컬 개발 등 Kafka 미연결 환경 — 발행 건너뜀(주문은 정상 생성, 체결엔진 처리만 생략)
            log.warn("Kafka disabled (app.kafka.enabled=false) — skip publish. topic={}, key={}", topic, key);
            return;
        }
        try {
            kafkaTemplate.send(topic, key, event).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "KAFKA_PUBLISH_FAILED", "주문 처리 중 오류가 발생했습니다.");
        }
    }
}
