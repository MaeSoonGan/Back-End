package com.mock.maesoongan.member.repository;

import com.mock.maesoongan.member.entity.Member;
import com.mock.maesoongan.member.entity.MemberStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByPhone(String phone);

    long countByStatus(MemberStatus status);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByEmail(String email);

    Optional<Member> findByPhone(String phone);

    Optional<Member> findByLoginIdAndNicknameAndEmail(String loginId, String nickname, String email);

    @Query("select coalesce(max(m.id), 0) from Member m")
    long findMaxId();
}
