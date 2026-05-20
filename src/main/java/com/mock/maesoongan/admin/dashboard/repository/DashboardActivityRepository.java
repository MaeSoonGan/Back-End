package com.mock.maesoongan.admin.dashboard.repository;

import com.mock.maesoongan.admin.dashboard.entity.DashboardActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardActivityRepository extends JpaRepository<DashboardActivity, Long> {

    List<DashboardActivity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
