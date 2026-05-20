package com.mock.maesoongan.admin.dashboard.repository;

import com.mock.maesoongan.admin.dashboard.entity.DashboardOrderStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DashboardOrderStatRepository extends JpaRepository<DashboardOrderStat, LocalDate> {

    List<DashboardOrderStat> findByOrderDateBetweenOrderByOrderDateAsc(LocalDate startDate, LocalDate endDate);
}
