package com.mock.maesoongan.admin.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_alerts")
public class DashboardAlert {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false, length = 50)
    private String userName;

    private Long orderId;

    @Column(nullable = false, length = 255)
    private String content;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    private LocalDateTime ignoredAt;

    @Column(length = 200)
    private String ignoreReason;

    protected DashboardAlert() {
    }

    public DashboardAlert(Long id, String type, long userId, String userName, Long orderId,
                          String content, String status, LocalDateTime detectedAt) {
        this.id = id;
        this.type = type;
        this.userId = userId;
        this.userName = userName;
        this.orderId = orderId;
        this.content = content;
        this.status = status;
        this.detectedAt = detectedAt;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getContent() {
        return content;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public boolean isProcessed() {
        return !"PENDING".equals(status);
    }

    public void ignore(LocalDateTime ignoredAt, String ignoreReason) {
        this.status = "IGNORED";
        this.ignoredAt = ignoredAt;
        this.ignoreReason = ignoreReason;
    }
}
