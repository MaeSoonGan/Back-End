package com.mock.maesoongan.admin.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seed_history")
public class SeedHistory {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SeedHistory() {
    }

    public SeedHistory(Long id, Long memberId, Long adminId, BigDecimal amount, String reason, LocalDateTime createdAt) {
        this.id = id;
        this.memberId = memberId;
        this.adminId = adminId;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
