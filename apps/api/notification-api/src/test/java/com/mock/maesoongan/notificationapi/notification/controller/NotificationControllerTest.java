package com.mock.maesoongan.notificationapi.notification.controller;

import com.mock.maesoongan.notificationapi.auth.CurrentMemberProvider;
import com.mock.maesoongan.notificationapi.common.BusinessException;
import com.mock.maesoongan.notificationapi.common.GlobalExceptionHandler;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationItem;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationListResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationSettingsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadAllNotificationsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadNotificationResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UnreadCountResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UpdateNotificationSettingsRequest;
import com.mock.maesoongan.notificationapi.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CurrentMemberProvider currentMemberProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService, currentMemberProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getNotificationsUsesConfiguredEndpoint() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(MEMBER_ID);
        when(notificationService.getNotifications(MEMBER_ID)).thenReturn(new NotificationListResponse(
                1,
                List.of(new NotificationItem(
                        10L,
                        "TRADE",
                        "Trade complete",
                        "Order filled",
                        false,
                        LocalDateTime.of(2026, 5, 25, 10, 0),
                        "ORDER",
                        100L
                ))
        ));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.unreadCount", is(1)))
                .andExpect(jsonPath("$.data.items[0].notificationId", is(10)))
                .andExpect(jsonPath("$.data.items[0].type", is("TRADE")));
    }

    @Test
    void markAsReadUsesConfiguredEndpoint() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(MEMBER_ID);
        when(notificationService.markAsRead(MEMBER_ID, 10L))
                .thenReturn(new ReadNotificationResponse(10L, true));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.notificationId", is(10)))
                .andExpect(jsonPath("$.data.isRead", is(true)));
    }

    @Test
    void markAsReadReturnsNotFoundWhenNotificationDoesNotExist() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(MEMBER_ID);
        when(notificationService.markAsRead(MEMBER_ID, 999L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notification not found"));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    void markAllAsReadUsesConfiguredEndpoint() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(MEMBER_ID);
        when(notificationService.markAllAsRead(MEMBER_ID))
                .thenReturn(new ReadAllNotificationsResponse(2));

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.updatedCount", is(2)));
    }

    @Test
    void getUnreadCountUsesConfiguredEndpoint() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(MEMBER_ID);
        when(notificationService.getUnreadCount(MEMBER_ID))
                .thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.unreadCount", is(3)));
    }

    @Test
    void getSettingsUsesConfiguredEndpoint() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(MEMBER_ID);
        when(notificationService.getSettings(MEMBER_ID))
                .thenReturn(settingsResponse());

        mockMvc.perform(get("/api/notifications/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tradeComplete", is(true)))
                .andExpect(jsonPath("$.data.marketClose", is(false)));
    }

    @Test
    void updateSettingsUsesConfiguredEndpoint() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(MEMBER_ID);
        when(notificationService.updateSettings(any(), any(UpdateNotificationSettingsRequest.class)))
                .thenReturn(settingsResponse());

        mockMvc.perform(patch("/api/notifications/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tradeComplete": true,
                                  "marketClose": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tradeComplete", is(true)))
                .andExpect(jsonPath("$.data.marketClose", is(false)));

        verify(notificationService).updateSettings(any(), any(UpdateNotificationSettingsRequest.class));
    }

    private NotificationSettingsResponse settingsResponse() {
        return new NotificationSettingsResponse(
                true,
                true,
                false,
                true,
                true,
                false,
                false,
                false
        );
    }
}
