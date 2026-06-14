package com.mock.maesoongan.adminservice.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 로그인 실패가 임계값(기본 5회) 이상 누적된 회원을 자동 정지하고,
 * 비정상 탐지(monitoring_status ABNORMAL_DETECTION) 알림을 생성한다.
 * - 회원별 트랜잭션으로 처리해 한 건 실패가 전체를 막지 않도록 함.
 * - 이미 정지(SUSPENDED)된 회원은 대상에서 제외(중복 방지).
 */
@Component
public class LoginFailureAutoSuspendScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureAutoSuspendScheduler.class);
    private static final String AUTO_REASON = "로그인 실패 누적 자동 정지";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int threshold;

    public LoginFailureAutoSuspendScheduler(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${admin.login-fail.suspend-threshold:5}") int threshold
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.threshold = threshold;
    }

    @Scheduled(fixedDelayString = "${admin.login-fail.scan-interval-ms:60000}")
    public void autoSuspendOnLoginFailures() {
        // 로그인 실패 누적(임계값 이상)인데 아직 활성 정지 원장이 없는 회원.
        // (auth-service가 5회 시 member_snapshot.status는 SUSPENDED로 바꾸지만 account_suspension 기록/알림은 안 만들기 때문)
        List<Long> targets = jdbcTemplate.queryForList("""
                select m.member_id
                from member_snapshot m
                where m.login_fail_count >= ?
                  and not exists (
                      select 1 from account_suspension s
                      where s.member_id = m.member_id and s.status <> 'RELEASED'
                  )
                """, Long.class, threshold);
        if (targets.isEmpty()) {
            return;
        }

        long adminId = systemAdminId();
        int suspended = 0;
        for (Long memberId : targets) {
            try {
                transactionTemplate.executeWithoutResult(status -> suspendMember(memberId, adminId));
                suspended++;
            } catch (RuntimeException exception) {
                log.warn("login-failure auto-suspend failed: memberId={}, err={}", memberId, exception.getMessage());
            }
        }
        if (suspended > 0) {
            log.info("login-failure auto-suspend: {} member(s) suspended (threshold={})", suspended, threshold);
        }
    }

    private void suspendMember(long memberId, long adminId) {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));

        // 정지 원장: 활성 기록이 없을 때만 추가
        Long active = jdbcTemplate.queryForObject(
                "select count(*) from account_suspension where member_id = ? and status <> 'RELEASED'",
                Long.class, memberId);
        if (active == null || active == 0) {
            jdbcTemplate.update("""
                    insert into account_suspension (member_id, admin_id, reason, status, created_at)
                    values (?, ?, ?, 'SUSPENDED', ?)
                    """, memberId, adminId, AUTO_REASON, now);
        }

        // 회원 상태 정지
        jdbcTemplate.update(
                "update member_snapshot set status = 'SUSPENDED', updated_at = ? where member_id = ?", now, memberId);

        // 비정상 탐지 알림(동일 회원 PENDING 중복 방지)
        Long pendingAlert = jdbcTemplate.queryForObject("""
                select count(*) from monitoring_status
                where status_type = 'ABNORMAL_DETECTION' and target_type = 'MEMBER'
                  and target_id = ? and status = 'PENDING'
                """, Long.class, memberId);
        if (pendingAlert == null || pendingAlert == 0) {
            jdbcTemplate.update("""
                    insert into monitoring_status (
                        status_type, target_type, target_id, service_name, severity, status,
                        title, message, is_maintenance, created_at, updated_at
                    )
                    values ('ABNORMAL_DETECTION', 'MEMBER', ?, 'AUTH', 'WARNING', 'PENDING', ?, ?, ?, ?, ?)
                    """,
                    memberId,
                    "로그인 다수 실패",
                    "로그인 " + threshold + "회 이상 실패로 자동 정지됨",
                    false, now, now);
        }

        // 감사 로그
        jdbcTemplate.update("""
                insert into audit_log (admin_id, action, target_type, target_id, reason, result, created_at)
                values (?, 'AUTO_SUSPEND_LOGIN_FAIL', 'MEMBER', ?, ?, 'SUCCESS', ?)
                """, adminId, memberId, AUTO_REASON, LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
    }

    private long systemAdminId() {
        try {
            Long id = jdbcTemplate.queryForObject(
                    "select id from admin where status = 'ACTIVE' order by id asc limit 1", Long.class);
            return id == null ? 1L : id;
        } catch (RuntimeException exception) {
            return 1L;
        }
    }
}
