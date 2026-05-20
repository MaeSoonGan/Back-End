package com.mock.maesoongan.admin.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "dashboard_order_stats")
public class DashboardOrderStat {

    @Id
    private LocalDate orderDate;

    @Column(nullable = false)
    private long totalOrderCount;

    @Column(nullable = false)
    private long completedOrderCount;

    protected DashboardOrderStat() {
    }

    public DashboardOrderStat(LocalDate orderDate, long totalOrderCount, long completedOrderCount) {
        this.orderDate = orderDate;
        this.totalOrderCount = totalOrderCount;
        this.completedOrderCount = completedOrderCount;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public long getTotalOrderCount() {
        return totalOrderCount;
    }

    public long getCompletedOrderCount() {
        return completedOrderCount;
    }
}
