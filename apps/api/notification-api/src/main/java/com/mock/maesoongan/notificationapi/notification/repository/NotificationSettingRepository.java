package com.mock.maesoongan.notificationapi.notification.repository;

import com.mock.maesoongan.notificationapi.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByMemberId(Long memberId);
}
