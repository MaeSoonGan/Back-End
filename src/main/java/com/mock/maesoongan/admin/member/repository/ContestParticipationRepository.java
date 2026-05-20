package com.mock.maesoongan.admin.member.repository;

import com.mock.maesoongan.admin.member.entity.ContestParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContestParticipationRepository extends JpaRepository<ContestParticipation, Long> {

    int countByMemberId(Long memberId);

    @Query("select avg(c.profitRate) from ContestParticipation c where c.memberId = :memberId and c.profitRate is not null")
    Double findAverageProfitRateByMemberId(Long memberId);

    Optional<ContestParticipation> findFirstByMemberIdOrderByJoinedAtDesc(Long memberId);
}
