package com.mock.maesoongan.admin.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_members")
public class DashboardMember {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime suspendedAt;

    @Column(length = 200)
    private String suspendReason;

    protected DashboardMember() {
    }

    public DashboardMember(Long id, String name, String status, LocalDateTime joinedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public boolean isSuspended() {
        return "SUSPENDED".equals(status);
    }

    public void suspend(LocalDateTime suspendedAt, String suspendReason) {
        this.status = "SUSPENDED";
        this.suspendedAt = suspendedAt;
        this.suspendReason = suspendReason;
    }
}
