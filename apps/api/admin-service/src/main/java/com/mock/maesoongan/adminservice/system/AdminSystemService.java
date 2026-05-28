package com.mock.maesoongan.adminservice.system;

import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.ActiveUsersResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AdminListItem;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AuditLogDetailResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AlertMutationResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AuditLogItem;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.AuditLogSummaryResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.ForceCancelOrderRequest;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.ForceCancelOrderResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.IgnoreAlertRequest;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MaintenanceResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MaintenanceUpdateRequest;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MonitoringAlertItem;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.MonitoringResponse;
import com.mock.maesoongan.adminservice.system.AdminSystemDtos.PageResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminSystemService {

    private static final int DEFAULT_ADMIN_ID = 1;

    private final JdbcTemplate jdbcTemplate;

    public AdminSystemService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public MonitoringResponse getMonitoring() {
        LocalDate today = LocalDate.now();
        return new MonitoringResponse(
                todayOrders(today),
                todayCompletedOrders(today),
                activeUserCount(),
                abnormalAlertCount(),
                getMaintenance(),
                getAlerts("PENDING", 0, 10).content(),
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public ActiveUsersResponse getActiveUsers() {
        return new ActiveUsersResponse(activeUserCount());
    }

    @Transactional(readOnly = true)
    public PageResponse<MonitoringAlertItem> getAlerts(String status, int page, int size) {
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "PENDING", "IGNORED", "RESOLVED"), "alert status", "PENDING");
        List<Object> args = new ArrayList<>();
        String where = "where ms.status_type = 'ABNORMAL_DETECTION'";

        if (!"ALL".equals(normalizedStatus)) {
            where += " and ms.status = ?";
            args.add(normalizedStatus);
        }

        long total = count("select count(*) from monitoring_status ms " + where, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(page * size);

        List<MonitoringAlertItem> content = jdbcTemplate.query("""
                        select ms.id,
                               ms.target_type,
                               ms.target_id,
                               ms.status,
                               ms.title,
                               ms.message,
                               ms.created_at,
                               coalesce(order_member.member_id, direct_member.member_id) as member_id,
                               coalesce(order_member.nickname, direct_member.nickname) as member_name,
                               case when ms.target_type = 'ORDER' then ms.target_id else null end as order_id
                        from monitoring_status ms
                        left join order_snapshot os on ms.target_type = 'ORDER' and os.order_id = ms.target_id
                        left join member_snapshot order_member on order_member.member_id = os.member_id
                        left join member_snapshot direct_member on ms.target_type = 'MEMBER' and direct_member.member_id = ms.target_id
                        %s
                        order by ms.created_at desc, ms.id desc
                        limit ? offset ?
                        """.formatted(where),
                (rs, rowNum) -> new MonitoringAlertItem(
                        rs.getLong("id"),
                        alertType(rs.getString("target_type")),
                        rs.getString("target_type"),
                        rs.getObject("target_id", Long.class),
                        rs.getObject("member_id", Long.class),
                        rs.getString("member_name"),
                        rs.getObject("order_id", Long.class),
                        rs.getString("title"),
                        firstNotBlank(rs.getString("message"), rs.getString("title")),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                queryArgs.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional
    public AlertMutationResponse ignoreAlert(long alertId, IgnoreAlertRequest request) {
        ensureAlertExists(alertId);
        long adminId = currentAdminId();
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update("""
                update monitoring_status
                set status = 'IGNORED',
                    ignored_by_admin_id = ?,
                    ignored_at = ?,
                    updated_at = ?
                where id = ? and status_type = 'ABNORMAL_DETECTION'
                """, adminId, now, now, alertId);

        insertAudit(adminId, "IGNORE_ABNORMAL_ALERT", "MONITORING", alertId, cleanReason(request == null ? null : request.reason(), "Abnormal alert ignored"));
        return new AlertMutationResponse(alertId, "IGNORED", "Alert ignored");
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse getMaintenance() {
        MaintenanceRow row = findMaintenance();
        if (row == null) {
            return new MaintenanceResponse("OFF", false, "System is operating normally.", null);
        }
        boolean enabled = "ENABLED".equals(row.status()) || row.isMaintenance();
        return new MaintenanceResponse(enabled ? "ON" : "OFF", enabled, row.message(), row.updatedAt());
    }

    @Transactional
    public MaintenanceResponse updateMaintenance(MaintenanceUpdateRequest request) {
        String status = normalizeMaintenanceStatus(request == null ? null : request.status());
        boolean enabled = "ENABLED".equals(status);
        String message = cleanReason(request == null ? null : request.message(), enabled ? "System maintenance is enabled." : "System is operating normally.");
        LocalDateTime now = LocalDateTime.now();
        MaintenanceRow existing = findMaintenance();

        if (existing == null) {
            jdbcTemplate.update("""
                    insert into monitoring_status (
                        status_type,
                        target_type,
                        service_name,
                        severity,
                        status,
                        title,
                        message,
                        is_maintenance,
                        created_at,
                        updated_at
                    )
                    values ('MAINTENANCE', 'SYSTEM', 'GLOBAL', 'INFO', ?, 'Maintenance mode', ?, ?, ?, ?)
                    """, status, message, enabled, now, now);
        } else {
            jdbcTemplate.update("""
                    update monitoring_status
                    set status = ?,
                        message = ?,
                        is_maintenance = ?,
                        updated_at = ?
                    where id = ?
                    """, status, message, enabled, now, existing.id());
        }

        insertAudit(currentAdminId(), enabled ? "ENABLE_MAINTENANCE" : "DISABLE_MAINTENANCE", "SYSTEM", 0, message);
        return new MaintenanceResponse(enabled ? "ON" : "OFF", enabled, message, now);
    }

    @Transactional(readOnly = true)
    public AuditLogSummaryResponse getAuditLogSummary() {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.now();
        return new AuditLogSummaryResponse(
                count("select count(*) from audit_log"),
                count("select count(*) from audit_log where created_at >= ? and created_at < ?", today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                count("select count(*) from audit_log where created_at >= ? and created_at < ?", month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay()),
                count("select count(*) from admin where deleted_at is null")
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogItem> getAuditLogs(
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            String type,
            Long adminId,
            int page,
            int size
    ) {
        validatePage(page, size);
        validateDateRange(startDate, endDate);
        AuditFilter filter = auditFilter(keyword, startDate, endDate, type, adminId);

        long total = count("""
                select count(*)
                from audit_log al
                left join admin a on a.id = al.admin_id
                %s
                """.formatted(filter.whereClause()), filter.args().toArray());

        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add(page * size);

        List<AuditLogItem> content = jdbcTemplate.query("""
                        select al.id,
                               al.action,
                               al.target_type,
                               al.target_id,
                               al.reason,
                               al.admin_id,
                               coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                               al.ip_address,
                               al.created_at
                        from audit_log al
                        left join admin a on a.id = al.admin_id
                        %s
                        order by al.created_at desc, al.id desc
                        limit ? offset ?
                        """.formatted(filter.whereClause()),
                (rs, rowNum) -> {
                    String action = rs.getString("action");
                    String targetType = rs.getString("target_type");
                    return new AuditLogItem(
                            rs.getLong("id"),
                            auditType(action, targetType),
                            action,
                            targetType,
                            rs.getObject("target_id", Long.class),
                            rs.getString("reason"),
                            rs.getObject("admin_id", Long.class),
                            rs.getString("admin_name"),
                            rs.getString("ip_address"),
                            toLocalDateTime(rs.getTimestamp("created_at"))
                    );
                },
                args.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public AuditLogDetailResponse getAuditLog(long logId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select al.id,
                                   al.action,
                                   al.target_type,
                                   al.target_id,
                                   al.reason,
                                   al.result,
                                   al.admin_id,
                                   a.login_id,
                                   coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                                   a.role,
                                   al.ip_address,
                                   al.user_agent,
                                   al.created_at
                            from audit_log al
                            left join admin a on a.id = al.admin_id
                            where al.id = ?
                            """,
                    (rs, rowNum) -> {
                        String action = rs.getString("action");
                        String targetType = rs.getString("target_type");
                        return new AuditLogDetailResponse(
                                rs.getLong("id"),
                                auditType(action, targetType),
                                action,
                                targetType,
                                rs.getObject("target_id", Long.class),
                                rs.getString("reason"),
                                rs.getString("result"),
                                rs.getObject("admin_id", Long.class),
                                rs.getString("login_id"),
                                rs.getString("admin_name"),
                                rs.getString("role"),
                                rs.getString("ip_address"),
                                rs.getString("user_agent"),
                                toLocalDateTime(rs.getTimestamp("created_at"))
                        );
                    },
                    logId);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("Audit log not found");
        }
    }

    @Transactional
    public ForceCancelOrderResponse forceCancelOrder(long orderId, ForceCancelOrderRequest request) {
        OrderStatusRow order = findOrderStatus(orderId);
        if (order == null) {
            throw notFound("Order not found");
        }
        if ("CANCELED".equals(order.status())) {
            return new ForceCancelOrderResponse(orderId, order.status(), order.status(), "Order is already canceled");
        }
        if ("FILLED".equals(order.status()) || "REJECTED".equals(order.status())) {
            throw badRequest("Only open or partially filled orders can be canceled");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                update order_snapshot
                set status = 'CANCELED',
                    remaining_quantity = 0,
                    updated_at = ?,
                    synced_at = ?
                where order_id = ?
                """, now, now, orderId);

        jdbcTemplate.update("""
                update monitoring_status
                set status = 'RESOLVED',
                    resolved_at = ?,
                    updated_at = ?
                where status_type = 'ABNORMAL_DETECTION'
                  and target_type = 'ORDER'
                  and target_id = ?
                  and status = 'PENDING'
                """, now, now, orderId);

        insertAudit(
                currentAdminId(),
                "FORCE_CANCEL_ORDER",
                "ORDER",
                orderId,
                cleanReason(request == null ? null : request.reason(), "Order force canceled by admin")
        );
        return new ForceCancelOrderResponse(orderId, order.status(), "CANCELED", "Order canceled");
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> getAdmins() {
        return jdbcTemplate.query("""
                select id, login_id, nickname, email, role, status
                from admin
                where deleted_at is null
                order by id asc
                """, (rs, rowNum) -> new AdminListItem(
                rs.getLong("id"),
                rs.getString("login_id"),
                rs.getString("nickname"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status")
        ));
    }

    private long todayOrders(LocalDate today) {
        return count("""
                select count(*)
                from order_snapshot
                where ordered_at >= ? and ordered_at < ?
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private long todayCompletedOrders(LocalDate today) {
        return count("""
                select count(*)
                from order_snapshot
                where ordered_at >= ? and ordered_at < ? and status = 'FILLED'
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private long activeUserCount() {
        return count("""
                select count(distinct member_id)
                from auth_token
                where member_id is not null and revoked = false and expires_at > ?
                """, LocalDateTime.now());
    }

    private long abnormalAlertCount() {
        return count("""
                select count(*)
                from monitoring_status
                where status_type = 'ABNORMAL_DETECTION' and status = 'PENDING'
                """);
    }

    private AuditFilter auditFilter(String keyword, LocalDate startDate, LocalDate endDate, String type, Long adminId) {
        String normalizedType = normalizeStatus(type, List.of("ALL", "MEMBER", "SEED", "ORDER", "CONTEST", "NOTICE", "RANKING", "SYSTEM"), "audit type", "ALL");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (normalizedKeyword != null) {
            conditions.add("(al.action like ? or al.target_type like ? or cast(al.target_id as char) like ? or al.reason like ? or a.nickname like ? or a.login_id like ?)");
            String like = "%" + normalizedKeyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (startDate != null) {
            conditions.add("al.created_at >= ?");
            args.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            conditions.add("al.created_at < ?");
            args.add(endDate.plusDays(1).atStartOfDay());
        }
        if (adminId != null) {
            if (adminId <= 0) {
                throw badRequest("adminId must be greater than 0");
            }
            conditions.add("al.admin_id = ?");
            args.add(adminId);
        }
        addAuditTypeCondition(conditions, args, normalizedType);

        return new AuditFilter(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
    }

    private void addAuditTypeCondition(List<String> conditions, List<Object> args, String type) {
        if ("ALL".equals(type)) {
            return;
        }
        if ("MEMBER".equals(type)) {
            conditions.add("(al.target_type = 'MEMBER' or al.action like ?)");
            args.add("%MEMBER%");
            return;
        }
        if ("SEED".equals(type)) {
            conditions.add("(al.target_type = 'SEED' or al.action like ?)");
            args.add("%SEED%");
            return;
        }
        if ("ORDER".equals(type)) {
            conditions.add("(al.target_type = 'ORDER' or al.action like ? or al.action like ?)");
            args.add("%ORDER%");
            args.add("%CANCEL%");
            return;
        }
        conditions.add("(al.target_type = ? or al.action like ?)");
        args.add(type);
        args.add("%" + type + "%");
    }

    private MaintenanceRow findMaintenance() {
        try {
            return jdbcTemplate.queryForObject("""
                            select id, status, message, is_maintenance, coalesce(updated_at, created_at) as updated_at
                            from monitoring_status
                            where status_type = 'MAINTENANCE' and target_type = 'SYSTEM'
                            order by coalesce(updated_at, created_at) desc, id desc
                            limit 1
                            """,
                    (rs, rowNum) -> new MaintenanceRow(
                            rs.getLong("id"),
                            rs.getString("status"),
                            rs.getString("message"),
                            rs.getBoolean("is_maintenance"),
                            toLocalDateTime(rs.getTimestamp("updated_at"))
                    ));
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void ensureAlertExists(long alertId) {
        if (count("select count(*) from monitoring_status where id = ? and status_type = 'ABNORMAL_DETECTION'", alertId) == 0) {
            throw notFound("Monitoring alert not found");
        }
    }

    private OrderStatusRow findOrderStatus(long orderId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select order_id, status
                            from order_snapshot
                            where order_id = ?
                            """,
                    (rs, rowNum) -> new OrderStatusRow(
                            rs.getLong("order_id"),
                            rs.getString("status")
                    ),
                    orderId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private long currentAdminId() {
        try {
            Long id = jdbcTemplate.queryForObject("select id from admin where status = 'ACTIVE' order by id asc limit 1", Long.class);
            return id == null ? DEFAULT_ADMIN_ID : id;
        } catch (EmptyResultDataAccessException exception) {
            return DEFAULT_ADMIN_ID;
        }
    }

    private void insertAudit(long adminId, String action, String targetType, long targetId, String reason) {
        jdbcTemplate.update("""
                insert into audit_log (admin_id, action, target_type, target_id, reason, result, created_at)
                values (?, ?, ?, ?, ?, 'SUCCESS', ?)
                """, adminId, action, targetType, targetId, reason, LocalDateTime.now());
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private int totalPages(long total, int size) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw badRequest("page must be greater than or equal to 0");
        }
        if (size <= 0 || size > 100) {
            throw badRequest("size must be between 1 and 100");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw badRequest("startDate must be before or equal to endDate");
        }
    }

    private String normalizeStatus(String value, List<String> allowed, String label, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw badRequest("Invalid " + label + ": " + value);
        }
        return normalized;
    }

    private String normalizeMaintenanceStatus(String value) {
        String normalized = value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
        if ("ON".equals(normalized) || "ENABLED".equals(normalized)) {
            return "ENABLED";
        }
        if ("OFF".equals(normalized) || "DISABLED".equals(normalized)) {
            return "DISABLED";
        }
        throw badRequest("status must be ON or OFF");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cleanReason(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String alertType(String targetType) {
        return "ORDER".equals(targetType) ? "ABNORMAL_ORDER" : "ABNORMAL_MEMBER";
    }

    private String auditType(String action, String targetType) {
        String source = ((action == null ? "" : action) + "_" + (targetType == null ? "" : targetType)).toUpperCase(Locale.ROOT);
        if (source.contains("SEED")) {
            return "SEED";
        }
        if (source.contains("ORDER") || source.contains("CANCEL")) {
            return "ORDER";
        }
        if (source.contains("CONTEST")) {
            return "CONTEST";
        }
        if (source.contains("NOTICE")) {
            return "NOTICE";
        }
        if (source.contains("RANKING") || source.contains("RANK")) {
            return "RANKING";
        }
        if (source.contains("SYSTEM") || source.contains("MAINTENANCE") || source.contains("MONITORING")) {
            return "SYSTEM";
        }
        if (source.contains("MEMBER") || source.contains("USER")) {
            return "MEMBER";
        }
        return "SYSTEM";
    }

    private String firstNotBlank(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? "" : fallback;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private record AuditFilter(String whereClause, List<Object> args) {
    }

    private record MaintenanceRow(long id, String status, String message, boolean isMaintenance, LocalDateTime updatedAt) {
    }

    private record OrderStatusRow(long orderId, String status) {
    }
}
