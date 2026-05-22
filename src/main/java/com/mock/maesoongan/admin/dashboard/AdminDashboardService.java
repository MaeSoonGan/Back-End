package com.mock.maesoongan.admin.dashboard;

import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ActivityListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ActivitySummary;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ActiveContestSummary;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.AlertListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.AlertSummary;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.CancelOrderResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestDetail;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestStatisticsResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DailyOrder;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DailyOrderListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DashboardResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.IgnoreAlertResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.SuspendUserResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.TodayOrderStatisticsResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.UserStatisticsResponse;
import com.mock.maesoongan.common.BusinessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final long ADMIN_ID = 1L;
    private static final List<String> ALERT_STATUSES = List.of("PENDING", "IGNORED", "RESOLVED");
    private static final List<String> CONTEST_STATUSES = List.of("SCHEDULED", "ACTIVE", "CLOSING_SOON", "ENDED", "CANCELED");
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MM.dd");

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardResponse getDashboard() {
        UserStatisticsResponse users = getUserStatistics();
        TodayOrderStatisticsResponse orders = getTodayOrderStatistics();
        ContestStatisticsResponse contests = getContestStatistics();
        AlertListResponse alerts = getAlerts("PENDING", 10);

        return new DashboardResponse(
                users.totalUsers(),
                users.todayNewUsers(),
                orders.totalOrderCount(),
                orders.completedOrderCount(),
                contests.activeContestCount(),
                contests.totalParticipantCount(),
                alerts.alertCount(),
                alerts.alerts(),
                getDailyOrders(7).orders(),
                getContestSummaries(),
                getActivities(5).activities(),
                LocalDateTime.now()
        );
    }

    public UserStatisticsResponse getUserStatistics() {
        LocalDate today = LocalDate.now();
        long totalUsers = count("select count(*) from member_snapshot where status <> 'DELETED'");
        long todayNewUsers = count("""
                select count(*)
                from member_snapshot
                where created_at >= ? and created_at < ? and status <> 'DELETED'
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        return new UserStatisticsResponse(totalUsers, todayNewUsers);
    }

    public TodayOrderStatisticsResponse getTodayOrderStatistics() {
        LocalDate today = LocalDate.now();
        long total = count("""
                select count(*)
                from order_snapshot
                where ordered_at >= ? and ordered_at < ?
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long completed = count("""
                select count(*)
                from order_snapshot
                where ordered_at >= ? and ordered_at < ? and status = 'FILLED'
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        return new TodayOrderStatisticsResponse(total, completed);
    }

    public ContestStatisticsResponse getContestStatistics() {
        long activeContestCount = count("select count(*) from contest where status in ('ACTIVE', 'CLOSING_SOON')");
        long participantCount = count("""
                select count(*)
                from contest_participation cp
                join contest c on c.id = cp.contest_id
                where c.status in ('ACTIVE', 'CLOSING_SOON') and cp.status = 'ACTIVE'
                """);

        return new ContestStatisticsResponse(activeContestCount, participantCount);
    }

    public AlertListResponse getAlerts(String status, int limit) {
        validateLimit(limit);
        if (status != null && !ALERT_STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ALERT_STATUS", "잘못된 알림 상태입니다.");
        }

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select ms.id,
                       ms.target_type,
                       ms.target_id,
                       ms.status,
                       ms.message,
                       ms.created_at,
                       coalesce(order_member.member_id, member_direct.member_id, 0) as user_id,
                       coalesce(order_member.nickname, member_direct.nickname, '알 수 없음') as user_name,
                       case when ms.target_type = 'ORDER' then ms.target_id else null end as order_id
                from monitoring_status ms
                left join order_snapshot os on ms.target_type = 'ORDER' and os.order_id = ms.target_id
                left join member_snapshot order_member on order_member.member_id = os.member_id
                left join member_snapshot member_direct on ms.target_type = 'MEMBER' and member_direct.member_id = ms.target_id
                where ms.status_type = 'ABNORMAL_DETECTION'
                """);
        if (status != null) {
            sql.append(" and ms.status = ?");
            params.add(status);
        }
        sql.append(" order by ms.created_at desc limit ?");
        params.add(limit);

        List<AlertSummary> alerts = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AlertSummary(
                rs.getLong("id"),
                "ORDER".equals(rs.getString("target_type")) ? "ABNORMAL_ORDER" : "ABNORMAL_MEMBER",
                rs.getLong("user_id"),
                rs.getString("user_name"),
                rs.getObject("order_id", Long.class),
                rs.getString("message"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), params.toArray());

        long alertCount = status == null
                ? count("select count(*) from monitoring_status where status_type = 'ABNORMAL_DETECTION'")
                : count("select count(*) from monitoring_status where status_type = 'ABNORMAL_DETECTION' and status = ?", status);
        String systemStatus = count("select count(*) from monitoring_status where status_type = 'ABNORMAL_DETECTION' and status = 'PENDING'") > 0
                ? "WARNING"
                : "NORMAL";

        return new AlertListResponse(alertCount, systemStatus, alerts);
    }

    @Transactional
    public SuspendUserResponse suspendUser(long userId, String reason) {
        MemberStatusRow member = findMemberStatus(userId);
        if ("SUSPENDED".equals(member.status())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ALREADY_SUSPENDED", "이미 정지된 회원입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update member_snapshot set status = 'SUSPENDED', updated_at = ? where member_id = ?", now, userId);
        insertAccountSuspension(userId, reason == null ? "관리자 계정 정지" : reason, now);
        insertAuditLog("SUSPEND_MEMBER", "MEMBER", userId, reason, now);

        return new SuspendUserResponse(userId, "SUSPENDED", now, "회원 계정이 정지되었습니다.");
    }

    @Transactional
    public CancelOrderResponse cancelOrder(long orderId, String reason) {
        String status = queryForString("select status from order_snapshot where order_id = ?", orderId);
        if (status == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다.");
        }
        if (List.of("FILLED", "CANCELED", "REJECTED").contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORDER_NOT_CANCELABLE", "취소할 수 없는 주문입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                update order_snapshot
                set status = 'CANCELED', remaining_quantity = 0, updated_at = ?, synced_at = ?
                where order_id = ?
                """, now, now, orderId);
        insertAuditLog("FORCE_CANCEL", "ORDER", orderId, reason, now);

        return new CancelOrderResponse(orderId, "CANCELED", now, "주문이 강제 취소되었습니다.");
    }

    @Transactional
    public IgnoreAlertResponse ignoreAlert(long alertId, String reason) {
        String status = queryForString("""
                select status
                from monitoring_status
                where id = ? and status_type = 'ABNORMAL_DETECTION'
                """, alertId);
        if (status == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "알림을 찾을 수 없습니다.");
        }
        if (!"PENDING".equals(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ALREADY_PROCESSED_ALERT", "이미 처리된 알림입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                update monitoring_status
                set status = 'IGNORED', ignored_by_admin_id = ?, ignored_at = ?, updated_at = ?
                where id = ?
                """, ADMIN_ID, now, now, alertId);
        insertAuditLog("IGNORE_ALERT", "MONITORING_STATUS", alertId, reason, now);

        return new IgnoreAlertResponse(alertId, "IGNORED", now, "알림이 무시 처리되었습니다.");
    }

    public DailyOrderListResponse getDailyOrders(int days) {
        if (days < 1 || days > 31) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAYS", "조회 일수는 1일부터 31일 사이로 입력해주세요.");
        }

        LocalDate today = LocalDate.now();
        List<DailyOrder> orders = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long count = count("""
                    select count(*)
                    from order_snapshot
                    where ordered_at >= ? and ordered_at < ?
                    """, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            orders.add(new DailyOrder(date, count, date.equals(today)));
        }

        return new DailyOrderListResponse(days, orders);
    }

    public ContestListResponse getContests(String status) {
        if (status != null && !CONTEST_STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CONTEST_STATUS", "잘못된 대회 상태값입니다.");
        }

        String sql = """
                select c.id,
                       c.title,
                       date(c.start_at) as start_date,
                       date(c.end_at) as end_date,
                       c.max_participants,
                       c.status,
                       count(cp.member_id) as participant_count
                from contest c
                left join contest_participation cp on cp.contest_id = c.id and cp.status = 'ACTIVE'
                where %s
                group by c.id, c.title, c.start_at, c.end_at, c.max_participants, c.status
                order by c.start_at asc
                """.formatted(status == null ? "c.status in ('ACTIVE', 'CLOSING_SOON')" : "c.status = ?");

        Object[] params = status == null ? new Object[]{} : new Object[]{status};
        List<ContestDetail> contests = jdbcTemplate.query(sql, (rs, rowNum) -> {
            LocalDate start = rs.getDate("start_date").toLocalDate();
            LocalDate end = rs.getDate("end_date").toLocalDate();
            String contestStatus = rs.getString("status");
            return new ContestDetail(
                    rs.getLong("id"),
                    rs.getString("title"),
                    start,
                    end,
                    formatPeriod(start, end),
                    rs.getLong("participant_count"),
                    rs.getObject("max_participants", Long.class),
                    contestStatus,
                    statusName(contestStatus)
            );
        }, params);

        return new ContestListResponse(contests);
    }

    public ActivityListResponse getActivities(int limit) {
        validateLimit(limit);

        List<ActivitySummary> activities = jdbcTemplate.query("""
                select al.id,
                       al.action,
                       concat(al.action, ' 처리', case when al.reason is null then '' else concat(' - ', al.reason) end) as content,
                       al.admin_id,
                       coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                       al.created_at
                from audit_log al
                left join admin a on a.id = al.admin_id
                order by al.created_at desc
                limit ?
                """, (rs, rowNum) -> new ActivitySummary(
                rs.getLong("id"),
                rs.getString("action"),
                rs.getString("content"),
                rs.getLong("admin_id"),
                rs.getString("admin_name"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), limit);

        return new ActivityListResponse(activities);
    }

    private List<ActiveContestSummary> getContestSummaries() {
        return getContests(null).contests().stream()
                .map(contest -> new ActiveContestSummary(
                        contest.contestId(),
                        contest.contestName(),
                        contest.period(),
                        contest.participantCount(),
                        contest.status()
                ))
                .toList();
    }

    private MemberStatusRow findMemberStatus(long userId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select member_id, status
                    from member_snapshot
                    where member_id = ?
                    """, (rs, rowNum) -> new MemberStatusRow(rs.getLong("member_id"), rs.getString("status")), userId);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다.");
        }
    }

    private void insertAccountSuspension(long userId, String reason, LocalDateTime now) {
        jdbcTemplate.update("""
                insert into account_suspension
                (id, member_id, admin_id, reason, status, suspended_until, released_at, release_admin_id, created_at, updated_at)
                values (?, ?, ?, ?, 'SUSPENDED', null, null, null, ?, null)
                """, nextId("account_suspension"), userId, ADMIN_ID, reason, now);
    }

    private void insertAuditLog(String action, String targetType, long targetId, String reason, LocalDateTime now) {
        jdbcTemplate.update("""
                insert into audit_log
                (id, admin_id, action, target_type, target_id, reason, result, ip_address, user_agent, created_at)
                values (?, ?, ?, ?, ?, ?, 'SUCCESS', null, 'swagger', ?)
                """, nextId("audit_log"), ADMIN_ID, action, targetType, targetId, reason, now);
    }

    private String queryForString(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class, args);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private long nextId(String tableName) {
        return jdbcTemplate.queryForObject("select coalesce(max(id), 0) + 1 from " + tableName, Long.class);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        return PERIOD_FORMATTER.format(startDate) + "-" + PERIOD_FORMATTER.format(endDate);
    }

    private String statusName(String status) {
        return switch (status) {
            case "ACTIVE" -> "진행중";
            case "CLOSING_SOON" -> "마감임박";
            case "SCHEDULED" -> "예정";
            case "ENDED" -> "종료";
            case "CANCELED" -> "취소";
            default -> status;
        };
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "조회 개수는 1개부터 100개 사이로 입력해주세요.");
        }
    }

    private record MemberStatusRow(long memberId, String status) {
    }
}
