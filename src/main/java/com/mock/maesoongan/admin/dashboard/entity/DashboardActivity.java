package com.mock.maesoongan.admin.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_activities")
public class DashboardActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 255)
    private String content;

    @Column(nullable = false)
    private long adminId;

    @Column(nullable = false, length = 50)
    private String adminName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected DashboardActivity() {
    }

    public DashboardActivity(Long id, String type, String content, long adminId, String adminName, LocalDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.content = content;
        this.adminId = adminId;
        this.adminName = adminName;
        this.createdAt = createdAt;
    }

    public DashboardActivity(String type, String content, long adminId, String adminName, LocalDateTime createdAt) {
        this.type = type;
        this.content = content;
        this.adminId = adminId;
        this.adminName = adminName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public long getAdminId() {
        return adminId;
    }

    public String getAdminName() {
        return adminName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
