package com.mock.maesoongan.admin.member.repository;

import com.mock.maesoongan.admin.member.entity.AccountSuspension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountSuspensionRepository extends JpaRepository<AccountSuspension, Long> {

    @Query("select coalesce(max(a.id), 0) from AccountSuspension a")
    long findMaxId();
}
