package com.mock.maesoongan.admin.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 50)
    private String targetType;

    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(Long id, Long adminId, String action, String targetType, Long targetId, String reason, LocalDateTime createdAt) {
        this.id = id;
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
