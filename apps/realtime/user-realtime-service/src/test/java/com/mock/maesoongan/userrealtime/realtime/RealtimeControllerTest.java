package com.mock.maesoongan.userrealtime.realtime;

import com.mock.maesoongan.userrealtime.auth.CurrentMemberProvider;
import com.mock.maesoongan.userrealtime.common.BusinessException;
import com.mock.maesoongan.userrealtime.common.GlobalExceptionHandler;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.PublishResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RealtimeControllerTest {

    private RealtimeSessionManager sessionManager;
    private CurrentMemberProvider currentMemberProvider;
    private RealtimeEventPublisher eventPublisher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sessionManager = mock(RealtimeSessionManager.class);
        currentMemberProvider = mock(CurrentMemberProvider.class);
        eventPublisher = mock(RealtimeEventPublisher.class);
        mockMvc = standaloneSetup(
                        new RealtimeController(sessionManager, currentMemberProvider),
                        new InternalRealtimeTestController(eventPublisher)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void streamConnectsCurrentMember() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(sessionManager.connect(7L)).thenReturn(new SseEmitter(1000L));

        mockMvc.perform(get("/api/realtime/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sessionManager).connect(7L);
    }

    @Test
    void streamReturnsUnauthorizedWhenAuthenticationIsMissing() throws Exception {
        when(currentMemberProvider.memberId())
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication failed"));

        RealtimeController controller = new RealtimeController(sessionManager, currentMemberProvider);

        assertThatThrownBy(controller::stream)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("UNAUTHORIZED");
                });
    }

    @Test
    void publishOrderEventReturnsDeliveryResult() throws Exception {
        when(eventPublisher.publishOrderEvent(any())).thenReturn(new PublishResponse(
                7L,
                RealtimeEventType.ORDER_FILLED.name(),
                2,
                "Order event published"
        ));

        mockMvc.perform(post("/internal/realtime/test-events/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 7,
                                  "orderId": 5001,
                                  "contestId": 3,
                                  "status": "FILLED",
                                  "stockCode": "005930",
                                  "stockName": "Samsung",
                                  "executedQuantity": 10,
                                  "executedPrice": 75400
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.memberId", is(7)))
                .andExpect(jsonPath("$.data.eventName", is("ORDER_FILLED")))
                .andExpect(jsonPath("$.data.deliveredCount", is(2)));
    }

    @Test
    void publishOrderEventReturnsBadRequestWhenStatusIsMissing() throws Exception {
        mockMvc.perform(post("/internal/realtime/test-events/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 7,
                                  "orderId": 5001
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void publishNotificationEventReturnsDeliveryResult() throws Exception {
        when(eventPublisher.publishNotificationEvent(any())).thenReturn(new PublishResponse(
                7L,
                RealtimeEventType.NOTIFICATION_CREATED.name(),
                1,
                "Notification event published"
        ));

        mockMvc.perform(post("/internal/realtime/test-events/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 7,
                                  "notificationId": 100,
                                  "type": "TRADE",
                                  "title": "Filled",
                                  "body": "Order filled"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventName", is("NOTIFICATION_CREATED")))
                .andExpect(jsonPath("$.data.deliveredCount", is(1)));
    }

    @Test
    void publishNotificationEventReturnsBadRequestWhenTitleIsMissing() throws Exception {
        mockMvc.perform(post("/internal/realtime/test-events/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 7,
                                  "type": "TRADE",
                                  "body": "Order filled"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }
}
