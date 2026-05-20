package com.mock.maesoongan.admin.dashboard.repository;

import com.mock.maesoongan.admin.dashboard.entity.DashboardContest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DashboardContestRepository extends JpaRepository<DashboardContest, Long> {

    long countByStatusIn(Collection<String> statuses);

    List<DashboardContest> findByStatusInOrderByStartDateAsc(Collection<String> statuses);

    List<DashboardContest> findByStatusOrderByStartDateAsc(String status);
}
