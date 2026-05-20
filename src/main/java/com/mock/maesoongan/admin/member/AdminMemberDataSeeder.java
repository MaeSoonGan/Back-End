package com.mock.maesoongan.admin.member;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class AdminMemberDataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public AdminMemberDataSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        clearTables();
        seedMembers();
        seedContestParticipations();
        seedAccountSuspensions();
        seedAuditLogs();
    }

    private void clearTables() {
        jdbcTemplate.execute("delete from seed_history");
        jdbcTemplate.execute("delete from audit_log");
        jdbcTemplate.execute("delete from account_suspension");
        jdbcTemplate.execute("delete from contest_participation");
        jdbcTemplate.execute("delete from member");
        jdbcTemplate.execute("alter table member auto_increment = 1");
    }

    private void seedMembers() {
        LocalDate today = LocalDate.now();

        insertMember(1, "user001", "홍길동", "hong@naver.com", LocalDate.of(2025, 1, 15),
                "ACTIVE", 0, new BigDecimal("11200000"));
        insertMember(2, "user002", "김철수", "kim@gmail.com", LocalDate.of(2025, 2, 3),
                "ACTIVE", 0, new BigDecimal("9800000"));
        insertMember(3, "user003", "이영희", "lee@kakao.com", LocalDate.of(2025, 3, 21),
                "SUSPENDED", 5, null);

        for (long id = 4; id <= 1234; id++) {
            boolean suspended = id <= 38;
            LocalDate joinDate = id >= 1000 && id <= 1011
                    ? today
                    : LocalDate.of(2025, (int) ((id % 12) + 1), (int) ((id % 27) + 1));
            BigDecimal totalAsset = id % 5 == 0 ? null : BigDecimal.valueOf(5_000_000L + (id * 10_000L));

            insertMember(
                    id,
                    "user" + String.format("%03d", id),
                    "회원" + id,
                    "user" + id + "@example.com",
                    joinDate,
                    suspended ? "SUSPENDED" : "ACTIVE",
                    (int) (id % 6),
                    totalAsset
            );
        }
    }

    private void insertMember(long id, String loginId, String nickname, String email, LocalDate joinDate,
                              String status, int loginFailCount, BigDecimal totalAsset) {
        LocalDateTime createdAt = joinDate.atTime(14, 55, 35);
        LocalDateTime lockedAt = "SUSPENDED".equals(status) ? createdAt.plusDays(1) : null;

        jdbcTemplate.update("""
                        insert into member
                        (id, login_id, password, email, nickname, phone, login_fail_count, email_verified,
                         locked_at, deleted_at, status, total_asset, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, null)
                        """,
                id,
                loginId,
                "{noop}password",
                email,
                nickname,
                "010-0000-" + String.format("%04d", id % 10000),
                loginFailCount,
                true,
                lockedAt,
                status,
                totalAsset,
                createdAt
        );
    }

    private void seedContestParticipations() {
        long participationId = 1;
        participationId = insertParticipation(participationId, 1, 1, new BigDecimal("10000000"), new BigDecimal("12.4"), 3);
        participationId = insertParticipation(participationId, 2, 1, new BigDecimal("10000000"), new BigDecimal("8.2"), 10);
        participationId = insertParticipation(participationId, 1, 2, new BigDecimal("10000000"), new BigDecimal("-2.1"), 21);

        for (long memberId = 4; memberId <= 1234; memberId++) {
            int count = (int) (memberId % 4);
            for (int i = 1; i <= count; i++) {
                BigDecimal profitRate = BigDecimal.valueOf(Math.round((((memberId + i) % 35) - 10) * 0.7 * 10) / 10.0);
                participationId = insertParticipation(
                        participationId,
                        i,
                        memberId,
                        new BigDecimal("10000000"),
                        profitRate,
                        (int) ((memberId + i) % 100 + 1)
                );
            }
        }
    }

    private long insertParticipation(long id, long contestId, long memberId, BigDecimal seedMoney,
                                     BigDecimal profitRate, Integer rank) {
        jdbcTemplate.update("""
                        insert into contest_participation
                        (id, contest_id, member_id, seed_money, profit_rate, `rank`, joined_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, null)
                        """,
                id,
                contestId,
                memberId,
                seedMoney,
                profitRate,
                rank,
                LocalDateTime.of(2025, 5, 1, 9, 0).plusDays(memberId % 10)
        );
        return id + 1;
    }

    private void seedAccountSuspensions() {
        long id = 1;
        for (long memberId = 3; memberId <= 38; memberId++) {
            jdbcTemplate.update("""
                            insert into account_suspension
                            (id, member_id, admin_id, reason, status, created_at, updated_at)
                            values (?, ?, 1, ?, 'SUSPENDED', ?, null)
                            """,
                    id++,
                    memberId,
                    "관리자에 의한 계정 정지",
                    LocalDateTime.now().minusDays(memberId % 10)
            );
        }
    }

    private void seedAuditLogs() {
        jdbcTemplate.update("""
                        insert into audit_log
                        (id, admin_id, action, target_type, target_id, reason, created_at)
                        values (1, 1, 'SUSPEND_MEMBER', 'MEMBER', 3, ?, ?)
                        """,
                "초기 정지 더미 데이터",
                LocalDateTime.now().minusDays(1)
        );
    }
}
