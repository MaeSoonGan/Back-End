package com.mock.maesoongan.adminservice.dashboard;

import com.mock.maesoongan.adminservice.dashboard.AdminDashboardDtos.ActivitySummary;
import com.mock.maesoongan.adminservice.dashboard.AdminDashboardDtos.ActiveContestSummary;
import com.mock.maesoongan.adminservice.dashboard.AdminDashboardDtos.AlertSummary;
import com.mock.maesoongan.adminservice.dashboard.AdminDashboardDtos.DailyOrder;
import com.mock.maesoongan.adminservice.dashboard.AdminDashboardDtos.DashboardResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MM.dd");

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();

        return new DashboardResponse(
                getTotalUsers(),
                getTodayNewUsers(today),
                getTodayOrders(today),
                getTodayCompletedOrders(today),
                getActiveContestCount(),
                getActiveContestParticipants(),
                getAbnormalAlertCount(),
                getPendingAlerts(),
                getDailyOrders(today),
                getActiveContests(),
                getRecentActivities(),
                LocalDateTime.now()
        );
    }

    private long getTotalUsers() {
        return count("select count(*) from member_snapshot where status <> 'DELETED'");
    }

    private long getTodayNewUsers(LocalDate today) {
        return count("""
                select count(*)
                from member_snapshot
                where created_at >= ? and created_at < ? and status <> 'DELETED'
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private long getTodayOrders(LocalDate today) {
        return count("""
                select count(*)
                from order_snapshot
                where ordered_at >= ? and ordered_at < ?
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private long getTodayCompletedOrders(LocalDate today) {
        return count("""
                select count(*)
                from order_snapshot
                where ordered_at >= ? and ordered_at < ? and status = 'FILLED'
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private long getActiveContestCount() {
        return count("select count(*) from contest where status in ('ACTIVE', 'CLOSING_SOON')");
    }

    private long getActiveContestParticipants() {
        return count("""
                select count(*)
                from contest_participation cp
                join contest c on c.id = cp.contest_id
                where c.status in ('ACTIVE', 'CLOSING_SOON') and cp.status = 'ACTIVE'
                """);
    }

    private long getAbnormalAlertCount() {
        return count("""
                select count(*)
                from monitoring_status
                where status_type = 'ABNORMAL_DETECTION' and status = 'PENDING'
                """);
    }

    private List<AlertSummary> getPendingAlerts() {
        return jdbcTemplate.query("""
                select ms.id,
                       ms.target_type,
                       ms.target_id,
                       ms.title,
                       ms.message,
                       ms.created_at,
                       coalesce(order_member.member_id, direct_member.member_id, 0) as user_id,
                       coalesce(order_member.nickname, direct_member.nickname, '') as user_name,
                       case when ms.target_type = 'ORDER' then ms.target_id else null end as order_id
                from monitoring_status ms
                left join order_snapshot os on ms.target_type = 'ORDER' and os.order_id = ms.target_id
                left join member_snapshot order_member on order_member.member_id = os.member_id
                left join member_snapshot direct_member on ms.target_type = 'MEMBER' and direct_member.member_id = ms.target_id
                where ms.status_type = 'ABNORMAL_DETECTION' and ms.status = 'PENDING'
                order by ms.created_at desc
                limit 10
                """, (rs, rowNum) -> new AlertSummary(
                rs.getLong("id"),
                alertType(rs.getString("target_type")),
                rs.getLong("user_id"),
                rs.getString("user_name"),
                rs.getObject("order_id", Long.class),
                firstNotBlank(rs.getString("message"), rs.getString("title")),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ));
    }

    private List<DailyOrder> getDailyOrders(LocalDate today) {
        LocalDate startDate = today.minusDays(6);
        Map<LocalDate, Long> counts = new LinkedHashMap<>();

        for (int i = 0; i < 7; i++) {
            counts.put(startDate.plusDays(i), 0L);
        }

        jdbcTemplate.query("""
                select date(ordered_at) as order_date,
                       count(*) as order_count
                from order_snapshot
                where ordered_at >= ? and ordered_at < ?
                group by date(ordered_at)
                order by order_date asc
                """, rs -> {
            Date date = rs.getDate("order_date");
            if (date != null) {
                counts.put(date.toLocalDate(), rs.getLong("order_count"));
            }
        }, startDate.atStartOfDay(), today.plusDays(1).atStartOfDay());

        return counts.entrySet()
                .stream()
                .map(entry -> new DailyOrder(entry.getKey(), entry.getValue(), entry.getKey().equals(today)))
                .toList();
    }

    private List<ActiveContestSummary> getActiveContests() {
        return jdbcTemplate.query("""
                select c.id,
                       c.title,
                       date(c.start_at) as start_date,
                       date(c.end_at) as end_date,
                       c.status,
                       count(cp.member_id) as participant_count
                from contest c
                left join contest_participation cp on cp.contest_id = c.id and cp.status = 'ACTIVE'
                where c.status in ('ACTIVE', 'CLOSING_SOON')
                group by c.id, c.title, c.start_at, c.end_at, c.status
                order by c.end_at asc, c.id asc
                limit 5
                """, (rs, rowNum) -> {
            LocalDate start = rs.getDate("start_date").toLocalDate();
            LocalDate end = rs.getDate("end_date").toLocalDate();
            return new ActiveContestSummary(
                    rs.getLong("id"),
                    rs.getString("title"),
                    PERIOD_FORMATTER.format(start) + "-" + PERIOD_FORMATTER.format(end),
                    rs.getLong("participant_count"),
                    rs.getString("status")
            );
        });
    }

    private List<ActivitySummary> getRecentActivities() {
        return jdbcTemplate.query("""
                select al.id,
                       al.action,
                       al.target_type,
                       al.target_id,
                       al.reason,
                       al.admin_id,
                       coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                       al.created_at
                from audit_log al
                left join admin a on a.id = al.admin_id
                order by al.created_at desc
                limit 5
                """, (rs, rowNum) -> {
            String action = rs.getString("action");
            String targetType = rs.getString("target_type");
            Long targetId = rs.getObject("target_id", Long.class);
            String reason = rs.getString("reason");
            return new ActivitySummary(
                    rs.getLong("id"),
                    action,
                    activityContent(action, targetType, targetId, reason),
                    rs.getObject("admin_id", Long.class),
                    rs.getString("admin_name"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        });
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String alertType(String targetType) {
        return "ORDER".equals(targetType) ? "ABNORMAL_ORDER" : "ABNORMAL_MEMBER";
    }

    private String firstNotBlank(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? "" : fallback;
    }

    private String activityContent(String action, String targetType, Long targetId, String reason) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        String target = targetType == null || targetId == null ? "" : " " + targetType + " " + targetId;
        return action + target;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
