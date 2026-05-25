package com.mock.maesoongan.notificationapi.notification.repository;

import com.mock.maesoongan.notificationapi.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

    long countByMemberIdAndReadFalse(Long memberId);

    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
            set notification.read = true,
                notification.readAt = :readAt
            where notification.memberId = :memberId
              and notification.read = false
            """)
    int markAllAsReadByMemberId(@Param("memberId") Long memberId, @Param("readAt") LocalDateTime readAt);
}
