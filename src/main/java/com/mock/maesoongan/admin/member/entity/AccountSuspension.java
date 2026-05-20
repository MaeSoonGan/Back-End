package com.mock.maesoongan.admin.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_suspension")
public class AccountSuspension {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected AccountSuspension() {
    }

    public AccountSuspension(Long id, Long memberId, Long adminId, String reason, String status, LocalDateTime createdAt) {
        this.id = id;
        this.memberId = memberId;
        this.adminId = adminId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }
}
