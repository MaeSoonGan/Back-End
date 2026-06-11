package com.mock.maesoongan.userrealtime.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RealtimeSessionManagerTest {

    @Test
    void connectRegistersEmitterAndPublishDeliversToMember() {
        RealtimeSessionManager sessionManager = new RealtimeSessionManager(10_000L);

        SseEmitter emitter = sessionManager.connect(7L);

        assertThat(emitter).isNotNull();
        assertThat(sessionManager.totalConnectionCount()).isEqualTo(1);

        int deliveredCount = sessionManager.publish(7L, RealtimeEventType.ORDER_FILLED.name(), new RealtimeDtos.OrderEvent(
                7L,
                5001L,
                3L,
                "FILLED",
                "005930",
                "Samsung",
                10,
                new BigDecimal("75400"),
                "Order filled",
                java.time.LocalDateTime.now()
        ));

        assertThat(deliveredCount).isEqualTo(1);
    }

    @Test
    void publishReturnsZeroWhenMemberHasNoConnection() {
        RealtimeSessionManager sessionManager = new RealtimeSessionManager(10_000L);

        int deliveredCount = sessionManager.publish(999L, RealtimeEventType.ORDER_FILLED.name(), "event");

        assertThat(deliveredCount).isZero();
        assertThat(sessionManager.totalConnectionCount()).isZero();
    }

    @Test
    void heartbeatSendsHeartbeatToConnectedEmitters() {
        RealtimeSessionManager sessionManager = new RealtimeSessionManager(10_000L);
        sessionManager.connect(7L);

        assertThatCode(sessionManager::heartbeat).doesNotThrowAnyException();
        assertThat(sessionManager.totalConnectionCount()).isEqualTo(1);
    }
}
