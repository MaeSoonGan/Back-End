package com.mock.maesoongan.admin.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "dashboard_contests")
public class DashboardContest {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private long participantCount;

    private Long maxParticipantCount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 30)
    private String statusName;

    protected DashboardContest() {
    }

    public DashboardContest(Long id, String name, LocalDate startDate, LocalDate endDate,
                            long participantCount, Long maxParticipantCount, String status, String statusName) {
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.participantCount = participantCount;
        this.maxParticipantCount = maxParticipantCount;
        this.status = status;
        this.statusName = statusName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public long getParticipantCount() {
        return participantCount;
    }

    public Long getMaxParticipantCount() {
        return maxParticipantCount;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusName() {
        return statusName;
    }
}
