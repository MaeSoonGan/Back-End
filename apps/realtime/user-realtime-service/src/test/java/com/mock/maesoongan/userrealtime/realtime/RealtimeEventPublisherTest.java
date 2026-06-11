package com.mock.maesoongan.userrealtime.realtime;

import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.NotificationEvent;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.NotificationEventRequest;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.OrderEvent;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.OrderEventRequest;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.PublishResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeEventPublisherTest {

    private RealtimeSessionManager sessionManager;
    private RealtimeEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        sessionManager = mock(RealtimeSessionManager.class);
        eventPublisher = new RealtimeEventPublisher(sessionManager);
    }

    @Test
    void publishOrderEventUsesOrderFilledEventForFilledStatus() {
        when(sessionManager.publish(eq(7L), eq(RealtimeEventType.ORDER_FILLED.name()), any())).thenReturn(2);

        PublishResponse response = eventPublisher.publishOrderEvent(orderRequest("filled", null));

        assertThat(response.eventName()).isEqualTo("ORDER_FILLED");
        assertThat(response.deliveredCount()).isEqualTo(2);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(sessionManager).publish(eq(7L), eq("ORDER_FILLED"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("FILLED");
        assertThat(captor.getValue().orderId()).isEqualTo(5001L);
    }

    @Test
    void publishOrderEventUsesOrderFilledEventForPartiallyFilledStatus() {
        when(sessionManager.publish(eq(7L), eq(RealtimeEventType.ORDER_FILLED.name()), any())).thenReturn(1);

        PublishResponse response = eventPublisher.publishOrderEvent(orderRequest("PARTIALLY_FILLED", null));

        assertThat(response.eventName()).isEqualTo("ORDER_FILLED");
        assertThat(response.deliveredCount()).isEqualTo(1);
    }

    @Test
    void publishOrderEventUsesOrderCanceledEventForCanceledStatus() {
        when(sessionManager.publish(eq(7L), eq(RealtimeEventType.ORDER_CANCELED.name()), any())).thenReturn(1);

        PublishResponse response = eventPublisher.publishOrderEvent(orderRequest("canceled", "Canceled by user"));

        assertThat(response.eventName()).isEqualTo("ORDER_CANCELED");

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(sessionManager).publish(eq(7L), eq("ORDER_CANCELED"), captor.capture());
        assertThat(captor.getValue().message()).isEqualTo("Canceled by user");
    }

    @Test
    void publishOrderEventUsesStatusChangedEventForOtherStatus() {
        when(sessionManager.publish(eq(7L), eq(RealtimeEventType.ORDER_STATUS_CHANGED.name()), any())).thenReturn(0);

        PublishResponse response = eventPublisher.publishOrderEvent(orderRequest("PENDING", null));

        assertThat(response.eventName()).isEqualTo("ORDER_STATUS_CHANGED");
        assertThat(response.deliveredCount()).isZero();
    }

    @Test
    void publishNotificationEventUsesNotificationCreatedEvent() {
        when(sessionManager.publish(eq(7L), eq(RealtimeEventType.NOTIFICATION_CREATED.name()), any())).thenReturn(3);

        PublishResponse response = eventPublisher.publishNotificationEvent(new NotificationEventRequest(
                7L,
                100L,
                "TRADE",
                "Filled",
                "Order filled"
        ));

        assertThat(response.eventName()).isEqualTo("NOTIFICATION_CREATED");
        assertThat(response.deliveredCount()).isEqualTo(3);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sessionManager).publish(eq(7L), eq("NOTIFICATION_CREATED"), captor.capture());
        assertThat(captor.getValue().notificationId()).isEqualTo(100L);
        assertThat(captor.getValue().type()).isEqualTo("TRADE");
    }

    private OrderEventRequest orderRequest(String status, String message) {
        return new OrderEventRequest(
                7L,
                5001L,
                3L,
                status,
                "005930",
                "Samsung",
                10,
                new BigDecimal("75400"),
                message
        );
    }
}
