package com.mock.maesoongan.adminservice.member;

/**
 * 로그인 보안(정지/해제) 동기화 계약 DTO. 온프렘 event-contracts와 필드/이름 동일해야 한다.
 * - command: AWS → 온프렘 (관리자 정지/해제)
 * - event  : 온프렘 → AWS (자동정지/관리자정지결과/해제결과/명령거부)
 */
public final class MemberSecurityDtos {

    private MemberSecurityDtos() {
    }

    public static final String ACTION_SUSPEND = "SUSPEND";
    public static final String ACTION_RELEASE = "RELEASE";

    public static final String EVENT_LOGIN_AUTO_SUSPENDED = "LOGIN_AUTO_SUSPENDED";
    public static final String EVENT_ADMIN_SUSPENDED = "ADMIN_SUSPENDED";
    public static final String EVENT_LOCK_RELEASED = "LOCK_RELEASED";
    public static final String EVENT_COMMAND_REJECTED = "COMMAND_REJECTED";

    // AWS → 온프렘 (Kafka key = memberId)
    public record MemberSecurityCommand(
            String commandId,
            String action,        // SUSPEND / RELEASE
            Long memberId,
            String operator,
            String reason,
            String requestedAt    // ISO-8601 (+09:00)
    ) {
    }

    // 온프렘 → AWS (Kafka key = memberId)
    public record MemberSecurityEvent(
            String eventId,
            String eventType,     // LOGIN_AUTO_SUSPENDED / ADMIN_SUSPENDED / LOCK_RELEASED / COMMAND_REJECTED
            Long memberId,
            String loginId,
            String status,        // SUSPENDED / ACTIVE
            Integer loginFailCount,
            String commandId,     // 관리자 명령 결과/거부일 때만 (자동정지는 null)
            String reason,
            String occurredAt     // ISO-8601 (+09:00)
    ) {
    }
}
