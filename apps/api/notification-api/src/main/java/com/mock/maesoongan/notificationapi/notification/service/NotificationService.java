package com.mock.maesoongan.notificationapi.notification.service;

import com.mock.maesoongan.notificationapi.common.BusinessException;
import com.mock.maesoongan.notificationapi.notification.domain.Notification;
import com.mock.maesoongan.notificationapi.notification.domain.NotificationSetting;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationItem;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationListResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.NotificationSettingsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadAllNotificationsResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.ReadNotificationResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UnreadCountResponse;
import com.mock.maesoongan.notificationapi.notification.dto.NotificationDtos.UpdateNotificationSettingsRequest;
import com.mock.maesoongan.notificationapi.notification.repository.NotificationRepository;
import com.mock.maesoongan.notificationapi.notification.repository.NotificationSettingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationSettingRepository notificationSettingRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationSettingRepository = notificationSettingRepository;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long memberId) {
        long unreadCount = notificationRepository.countByMemberIdAndReadFalse(memberId);
        List<NotificationItem> items = notificationRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId)
                .stream()
                .map(this::toItem)
                .toList();

        return new NotificationListResponse(unreadCount, items);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long memberId) {
        return new UnreadCountResponse(notificationRepository.countByMemberIdAndReadFalse(memberId));
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getSettings(Long memberId) {
        NotificationSetting setting = notificationSettingRepository.findByMemberId(memberId)
                .orElseGet(() -> NotificationSetting.defaultFor(memberId));
        return toSettingsResponse(setting);
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(Long memberId, UpdateNotificationSettingsRequest request) {
        NotificationSetting setting = getOrCreateSetting(memberId);
        setting.updateTradeSettings(request.tradeComplete(), request.orderCancel(), request.pendingOrder());
        setting.updateContestSettings(request.contestStart(), request.contestEnd(), request.rankChange());
        setting.updateMarketSettings(request.marketOpen(), request.marketClose());
        return toSettingsResponse(setting);
    }

    @Transactional
    public ReadNotificationResponse markAsRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notification not found"));

        notification.markAsRead(LocalDateTime.now());
        return new ReadNotificationResponse(notification.getId(), notification.isRead());
    }

    @Transactional
    public ReadAllNotificationsResponse markAllAsRead(Long memberId) {
        int updatedCount = notificationRepository.markAllAsReadByMemberId(memberId, LocalDateTime.now());
        return new ReadAllNotificationsResponse(updatedCount);
    }

    private NotificationSetting getOrCreateSetting(Long memberId) {
        return notificationSettingRepository.findByMemberId(memberId)
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.defaultFor(memberId)));
    }

    private NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                normalizeType(notification.getType()),
                notification.getTitle(),
                notification.getBody(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    private NotificationSettingsResponse toSettingsResponse(NotificationSetting setting) {
        return new NotificationSettingsResponse(
                setting.isTradeComplete(),
                setting.isOrderCancel(),
                setting.isPendingOrder(),
                setting.isContestStart(),
                setting.isContestEnd(),
                setting.isRankChange(),
                setting.isMarketOpen(),
                setting.isMarketClose()
        );
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "NOTICE";
        }
        if (type.startsWith("TRADE")) {
            return "TRADE";
        }
        if (type.contains("ORDER_CANCEL") || type.contains("CANCELED") || type.contains("CANCELLED")) {
            return "ORDER_CANCEL";
        }
        if (type.startsWith("CONTEST")) {
            return "CONTEST";
        }
        if (type.startsWith("MARKET")) {
            return "MARKET";
        }
        if ("NOTICE".equals(type)) {
            return "NOTICE";
        }
        return type;
    }
}
