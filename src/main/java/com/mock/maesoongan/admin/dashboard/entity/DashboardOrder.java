package com.mock.maesoongan.admin.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_orders")
public class DashboardOrder {

    @Id
    private Long id;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime canceledAt;

    @Column(length = 200)
    private String cancelReason;

    protected DashboardOrder() {
    }

    public DashboardOrder(Long id, String status, LocalDateTime createdAt) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public boolean isCancelable() {
        return !("COMPLETED".equals(status) || "CANCELED".equals(status));
    }

    public void cancel(LocalDateTime canceledAt, String cancelReason) {
        this.status = "CANCELED";
        this.canceledAt = canceledAt;
        this.cancelReason = cancelReason;
    }
}
