package com.mock.maesoongan.admin.dashboard.repository;

import com.mock.maesoongan.admin.dashboard.entity.DashboardOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardOrderRepository extends JpaRepository<DashboardOrder, Long> {
}
