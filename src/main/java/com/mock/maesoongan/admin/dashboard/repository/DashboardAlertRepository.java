package com.mock.maesoongan.admin.dashboard.repository;

import com.mock.maesoongan.admin.dashboard.entity.DashboardAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardAlertRepository extends JpaRepository<DashboardAlert, Long> {

    long countByStatus(String status);

    List<DashboardAlert> findByStatusOrderByDetectedAtDesc(String status, Pageable pageable);

    List<DashboardAlert> findAllByOrderByDetectedAtDesc(Pageable pageable);
}
