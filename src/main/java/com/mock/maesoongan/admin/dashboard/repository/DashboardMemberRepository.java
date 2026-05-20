package com.mock.maesoongan.admin.dashboard.repository;

import com.mock.maesoongan.admin.dashboard.entity.DashboardMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface DashboardMemberRepository extends JpaRepository<DashboardMember, Long> {

    long countByJoinedAtGreaterThanEqualAndJoinedAtLessThan(LocalDateTime start, LocalDateTime end);
}
