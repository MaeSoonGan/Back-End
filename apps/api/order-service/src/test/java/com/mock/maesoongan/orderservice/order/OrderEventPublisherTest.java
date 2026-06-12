package com.mock.maesoongan.orderservice.order;

import com.mock.maesoongan.orderservice.order.OrderEvents.OrderCancelRequestedEvent;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final OrderEventPublisher publisher = new OrderEventPublisher(
            kafkaTemplate,
            "order.request",
            "order.cancel",
            Duration.ofSeconds(1),
            true
    );

    @Test
    void publishOrderRequestedUsesOnPremTopicKeyAndPayload() {
        OrderRequestedEvent event = new OrderRequestedEvent(
                990003L,
                1001L,
                "005930",
                "Samsung",
                "BUY",
                "LIMIT",
                new BigDecimal("336500"),
                1L,
                LocalDateTime.of(2026, 6, 12, 12, 50, 17)
        );
        when(kafkaTemplate.send(eq("order.request"), eq("990003"), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderRequested(event);

        verify(kafkaTemplate).send("order.request", "990003", event);
    }

    @Test
    void publishOrderCancelRequestedUsesOnPremTopicKeyAndPayload() {
        OrderCancelRequestedEvent event = new OrderCancelRequestedEvent(
                990003L,
                1001L,
                LocalDateTime.of(2026, 6, 12, 13, 0)
        );
        when(kafkaTemplate.send(eq("order.cancel"), eq("990003"), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderCancelRequested(event);

        verify(kafkaTemplate).send("order.cancel", "990003", event);
    }
}
