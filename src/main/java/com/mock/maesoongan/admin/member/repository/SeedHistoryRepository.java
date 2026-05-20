package com.mock.maesoongan.admin.member.repository;

import com.mock.maesoongan.admin.member.entity.SeedHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeedHistoryRepository extends JpaRepository<SeedHistory, Long> {

    @Query("select coalesce(max(s.id), 0) from SeedHistory s")
    long findMaxId();
}
