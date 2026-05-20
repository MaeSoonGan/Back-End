package com.mock.maesoongan.admin.member.repository;

import com.mock.maesoongan.admin.member.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("select coalesce(max(a.id), 0) from AuditLog a")
    long findMaxId();
}
