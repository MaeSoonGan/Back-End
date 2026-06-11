package com.mock.maesoongan.notificationapi.notification.service;

import com.mock.maesoongan.notificationapi.common.BusinessException;
import com.mock.maesoongan.notificationapi.notification.domain.Notification;
import com.mock.maesoongan.notificationapi.notification.domain.NotificationSetting;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationListResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationSettingsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadAllNotificationsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadNotificationResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UnreadCountResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UpdateNotificationSettingsRequest;
import com.mock.maesoongan.notificationapi.notification.repository.NotificationRepository;
import com.mock.maesoongan.notificationapi.notification.repository.NotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationSettingRepository);
    }

    @Test
    void getNotificationsReturnsUnreadCountAndItems() {
        Long memberId = 1L;
        Notification tradeNotification = notification(10L, memberId, "TRADE_EXECUTED", false);
        Notification cancelNotification = notification(11L, memberId, "ORDER_CANCELLED", true);
        Notification nullTypeNotification = notification(12L, memberId, null, false);

        when(notificationRepository.countByMemberIdAndReadFalse(memberId)).thenReturn(2L);
        when(notificationRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId))
                .thenReturn(List.of(tradeNotification, cancelNotification, nullTypeNotification));

        NotificationListResponse response = notificationService.getNotifications(memberId);

        assertEquals(2L, response.unreadCount());
        assertEquals(3, response.items().size());
        assertEquals(10L, response.items().get(0).notificationId());
        assertEquals("TRADE", response.items().get(0).type());
        assertEquals("ORDER_CANCEL", response.items().get(1).type());
        assertEquals("NOTICE", response.items().get(2).type());
        assertFalse(response.items().get(0).isRead());
    }

    @Test
    void getUnreadCountReturnsRepositoryCount() {
        Long memberId = 1L;
        when(notificationRepository.countByMemberIdAndReadFalse(memberId)).thenReturn(5L);

        UnreadCountResponse response = notificationService.getUnreadCount(memberId);

        assertEquals(5L, response.unreadCount());
    }

    @Test
    void getSettingsReturnsDefaultValuesWhenSettingDoesNotExist() {
        Long memberId = 1L;
        when(notificationSettingRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        NotificationSettingsResponse response = notificationService.getSettings(memberId);

        assertTrue(response.tradeComplete());
        assertTrue(response.orderCancel());
        assertFalse(response.pendingOrder());
        assertTrue(response.contestStart());
        assertTrue(response.contestEnd());
        assertFalse(response.rankChange());
        assertFalse(response.marketOpen());
        assertFalse(response.marketClose());
    }

    @Test
    void updateSettingsCreatesDefaultSettingAndAppliesPartialChanges() {
        Long memberId = 1L;
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                false,
                null,
                true,
                null,
                false,
                true,
                true,
                null
        );

        when(notificationSettingRepository.findByMemberId(memberId)).thenReturn(Optional.empty());
        when(notificationSettingRepository.save(any(NotificationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationSettingsResponse response = notificationService.updateSettings(memberId, request);

        assertFalse(response.tradeComplete());
        assertTrue(response.orderCancel());
        assertTrue(response.pendingOrder());
        assertTrue(response.contestStart());
        assertFalse(response.contestEnd());
        assertTrue(response.rankChange());
        assertTrue(response.marketOpen());
        assertFalse(response.marketClose());
        verify(notificationSettingRepository).save(any(NotificationSetting.class));
    }

    @Test
    void markAsReadUpdatesUnreadNotification() {
        Long memberId = 1L;
        Long notificationId = 10L;
        Notification notification = notification(notificationId, memberId, "NOTICE", false);

        when(notificationRepository.findByIdAndMemberId(notificationId, memberId))
                .thenReturn(Optional.of(notification));

        ReadNotificationResponse response = notificationService.markAsRead(memberId, notificationId);

        assertEquals(notificationId, response.notificationId());
        assertTrue(response.isRead());
        assertTrue(notification.isRead());
    }

    @Test
    void markAsReadThrowsNotFoundWhenNotificationDoesNotExist() {
        Long memberId = 1L;
        Long notificationId = 999L;

        when(notificationRepository.findByIdAndMemberId(notificationId, memberId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> notificationService.markAsRead(memberId, notificationId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.status());
        assertEquals("NOT_FOUND", exception.code());
    }

    @Test
    void markAllAsReadReturnsUpdatedCount() {
        Long memberId = 1L;
        when(notificationRepository.markAllAsReadByMemberId(any(), any())).thenReturn(3);

        ReadAllNotificationsResponse response = notificationService.markAllAsRead(memberId);

        assertEquals(3, response.updatedCount());
    }

    private Notification notification(Long id, Long memberId, String type, boolean read) {
        Notification notification = newNotification();
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "memberId", memberId);
        ReflectionTestUtils.setField(notification, "type", type);
        ReflectionTestUtils.setField(notification, "title", "Test title");
        ReflectionTestUtils.setField(notification, "body", "Test body");
        ReflectionTestUtils.setField(notification, "read", read);
        ReflectionTestUtils.setField(notification, "deliveryStatus", "SENT");
        ReflectionTestUtils.setField(notification, "retryCount", 0);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 5, 25, 10, 0));
        return notification;
    }

    private Notification newNotification() {
        try {
            Constructor<Notification> constructor = Notification.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create notification test fixture", exception);
        }
    }
}
