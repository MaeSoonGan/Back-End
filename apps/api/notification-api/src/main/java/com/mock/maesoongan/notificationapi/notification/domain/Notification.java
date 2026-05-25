package com.mock.maesoongan.notificationapi.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "delivery_status", nullable = false, length = 30)
    private String deliveryStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "delivery_failure_reason", length = 500)
    private String deliveryFailureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isRead() {
        return read;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getLastRetryAt() {
        return lastRetryAt;
    }

    public String getDeliveryFailureReason() {
        return deliveryFailureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void markAsRead(LocalDateTime readAt) {
        if (!read) {
            this.read = true;
            this.readAt = readAt;
        }
    }
}
