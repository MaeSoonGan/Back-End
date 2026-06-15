package com.mock.maesoongan.adminservice.member;

import com.mock.maesoongan.adminservice.member.MemberSecurityDtos.MemberSecurityCommand;
import com.mock.maesoongan.adminservice.member.MemberSecurityDtos.MemberSecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 로그인 보안(정지/해제) — AWS 측 처리. (별도 추적 테이블 없이 memberId 기준 + 자연 멱등으로 처리)
 * - 관리자 정지/해제: member.security.command 발행 (원장 성공 이벤트 기준 확정)
 * - 온프렘 member.security.event 수신: 비정상탐지 알림 생애주기 + 정지 원장 갱신
 * (member_snapshot의 status/login_fail_count 미러는 trade-sync-worker 담당)
 */
@Service
public class MemberSecurityService {

    private static final Logger log = LoggerFactory.getLogger(MemberSecurityService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String AUTO_REASON = "로그인 실패 누적 자동 정지";

    private final JdbcTemplate jdbcTemplate;
    private final MemberSecurityCommandPublisher commandPublisher;

    public MemberSecurityService(JdbcTemplate jdbcTemplate, MemberSecurityCommandPublisher commandPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.commandPublisher = commandPublisher;
    }

    // ===== 관리자 명령 (AWS → 온프렘) =====

    public void requestSuspend(long memberId, long adminId, String reason) {
        publishCommand(memberId, MemberSecurityDtos.ACTION_SUSPEND, adminId, reason);
    }

    public void requestRelease(long memberId, long adminId, String reason) {
        publishCommand(memberId, MemberSecurityDtos.ACTION_RELEASE, adminId, reason);
    }

    private void publishCommand(long memberId, String action, long adminId, String reason) {
        // commandId는 온프렘 멱등/추적용으로 보내되 AWS는 별도 저장하지 않는다(자연 멱등으로 처리).
        commandPublisher.publish(new MemberSecurityCommand(
                UUID.randomUUID().toString(),
                action,
                memberId,
                String.valueOf(adminId),
                reason,
                OffsetDateTime.now(KST).toString()));
    }

    // ===== 온프렘 결과/통지 이벤트 (온프렘 → AWS) — memberId 기준, 모든 연산 멱등 =====

    @Transactional
    public void handleEvent(MemberSecurityEvent event) {
        if (event.eventType() == null || event.memberId() == null) {
            log.warn("Invalid member security event: eventType/memberId missing");
            return;
        }
        LocalDateTime now = LocalDateTime.now(KST);
        long memberId = event.memberId();
        switch (event.eventType()) {
            case MemberSecurityDtos.EVENT_LOGIN_AUTO_SUSPENDED -> {
                ensureActiveSuspension(memberId, AUTO_REASON, now);
                createAbnormalAlert(memberId, event.loginFailCount(), now);
            }
            case MemberSecurityDtos.EVENT_ADMIN_SUSPENDED ->
                    ensureActiveSuspension(memberId, reasonOr(event.reason(), "관리자 계정 정지"), now);
            case MemberSecurityDtos.EVENT_LOCK_RELEASED -> {
                releaseSuspensions(memberId, now);
                resolvePendingAlerts(memberId, now);
            }
            case MemberSecurityDtos.EVENT_COMMAND_REJECTED ->
                    log.warn("Member security command rejected. memberId={}, commandId={}, reason={}",
                            memberId, event.commandId(), event.reason());
            default -> log.warn("Unsupported member security eventType: {}", event.eventType());
        }
    }

    // 활성 정지 원장이 없을 때만 추가(멱등)
    private void ensureActiveSuspension(long memberId, String reason, LocalDateTime now) {
        Long active = jdbcTemplate.queryForObject(
                "select count(*) from account_suspension where member_id = ? and status <> 'RELEASED'",
                Long.class, memberId);
        if (active == null || active == 0) {
            jdbcTemplate.update("""
                    insert into account_suspension (member_id, admin_id, reason, status, created_at)
                    values (?, ?, ?, 'SUSPENDED', ?)
                    """, memberId, systemAdminId(), reason, now);
        }
    }

    // 동일 회원 PENDING 알림이 없을 때만 생성(멱등)
    private void createAbnormalAlert(long memberId, Integer loginFailCount, LocalDateTime now) {
        Long pending = jdbcTemplate.queryForObject("""
                select count(*) from monitoring_status
                where status_type = 'ABNORMAL_DETECTION' and target_type = 'MEMBER'
                  and target_id = ? and status = 'PENDING'
                """, Long.class, memberId);
        if (pending != null && pending > 0) {
            return;
        }
        int count = loginFailCount == null ? 5 : loginFailCount;
        jdbcTemplate.update("""
                insert into monitoring_status (
                    status_type, target_type, target_id, service_name, severity, status,
                    title, message, is_maintenance, created_at, updated_at
                )
                values ('ABNORMAL_DETECTION', 'MEMBER', ?, 'AUTH', 'WARNING', 'PENDING', ?, ?, ?, ?, ?)
                """,
                memberId, "로그인 다수 실패", "로그인 " + count + "회 이상 실패로 자동 정지됨", false, now, now);
    }

    private void releaseSuspensions(long memberId, LocalDateTime now) {
        jdbcTemplate.update("""
                update account_suspension
                set status = 'RELEASED', released_at = ?, release_admin_id = ?, updated_at = ?
                where member_id = ? and status <> 'RELEASED'
                """, now, systemAdminId(), now, memberId);
    }

    private void resolvePendingAlerts(long memberId, LocalDateTime now) {
        jdbcTemplate.update("""
                update monitoring_status
                set status = 'RESOLVED', updated_at = ?
                where status_type = 'ABNORMAL_DETECTION' and target_type = 'MEMBER'
                  and target_id = ? and status = 'PENDING'
                """, now, memberId);
    }

    private String reasonOr(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
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
