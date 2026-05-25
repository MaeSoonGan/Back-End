package com.mock.maesoongan.adminservice.notice;

import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeCreateRequest;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeDetailResponse;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeListItem;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeMutationResponse;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeUpdateRequest;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.PageResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminNoticeService {

    private static final int DEFAULT_ADMIN_ID = 1;

    private final JdbcTemplate jdbcTemplate;

    public AdminNoticeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<NoticeListItem> getNotices(String keyword, String status, int page, int size) {
        validatePage(page, size);
        NoticeFilter filter = noticeFilter(keyword, status);

        long total = count("select count(*) from notice n " + filter.whereClause(), filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add(page * size);

        List<NoticeListItem> content = jdbcTemplate.query("""
                        select n.id,
                               n.title,
                               n.is_pinned,
                               n.status,
                               n.start_at,
                               n.end_at,
                               n.admin_id,
                               coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                               n.created_at,
                               n.updated_at
                        from notice n
                        left join admin a on a.id = n.admin_id
                        %s
                        order by n.is_pinned desc, n.created_at desc, n.id desc
                        limit ? offset ?
                        """.formatted(filter.whereClause()),
                (rs, rowNum) -> new NoticeListItem(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getBoolean("is_pinned"),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("start_at")),
                        toLocalDateTime(rs.getTimestamp("end_at")),
                        rs.getObject("admin_id", Long.class),
                        rs.getString("admin_name"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ),
                args.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNotice(long noticeId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select n.id,
                                   n.title,
                                   n.content,
                                   n.is_pinned,
                                   n.status,
                                   n.start_at,
                                   n.end_at,
                                   n.admin_id,
                                   coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                                   n.created_at,
                                   n.updated_at
                            from notice n
                            left join admin a on a.id = n.admin_id
                            where n.id = ?
                            """,
                    (rs, rowNum) -> new NoticeDetailResponse(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getBoolean("is_pinned"),
                            rs.getString("status"),
                            toLocalDateTime(rs.getTimestamp("start_at")),
                            toLocalDateTime(rs.getTimestamp("end_at")),
                            rs.getObject("admin_id", Long.class),
                            rs.getString("admin_name"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at"))
                    ),
                    noticeId);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("Notice not found");
        }
    }

    @Transactional
    public NoticeMutationResponse createNotice(NoticeCreateRequest request) {
        LocalDateTime startAt = defaultStartAt(request.startAt());
        validatePeriod(startAt, request.endAt());
        String status = resolveStatus(request.status(), startAt);
        boolean isPinned = request.isPinned() != null && request.isPinned();
        long adminId = currentAdminId();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into notice (
                        admin_id,
                        title,
                        content,
                        is_pinned,
                        status,
                        start_at,
                        end_at,
                        created_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, adminId);
            ps.setString(2, request.title());
            ps.setString(3, request.content());
            ps.setBoolean(4, isPinned);
            ps.setString(5, status);
            ps.setTimestamp(6, Timestamp.valueOf(startAt));
            ps.setTimestamp(7, request.endAt() == null ? null : Timestamp.valueOf(request.endAt()));
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Notice creation failed");
        }

        long noticeId = key.longValue();
        insertAudit(adminId, "CREATE_NOTICE", "NOTICE", noticeId, request.title());
        return new NoticeMutationResponse(noticeId, status, "Notice created");
    }

    @Transactional
    public NoticeMutationResponse updateNotice(long noticeId, NoticeUpdateRequest request) {
        ensureNoticeExists(noticeId);
        LocalDateTime startAt = defaultStartAt(request.startAt());
        validatePeriod(startAt, request.endAt());
        String status = resolveStatus(request.status(), startAt);
        boolean isPinned = request.isPinned() != null && request.isPinned();

        jdbcTemplate.update("""
                update notice
                set title = ?,
                    content = ?,
                    is_pinned = ?,
                    status = ?,
                    start_at = ?,
                    end_at = ?,
                    updated_at = ?
                where id = ?
                """,
                request.title(),
                request.content(),
                isPinned,
                status,
                startAt,
                request.endAt(),
                LocalDateTime.now(),
                noticeId);

        insertAudit(currentAdminId(), "UPDATE_NOTICE", "NOTICE", noticeId, request.title());
        return new NoticeMutationResponse(noticeId, status, "Notice updated");
    }

    @Transactional
    public NoticeMutationResponse deleteNotice(long noticeId) {
        ensureNoticeExists(noticeId);
        jdbcTemplate.update("""
                update notice
                set status = 'HIDDEN',
                    updated_at = ?
                where id = ?
                """, LocalDateTime.now(), noticeId);
        insertAudit(currentAdminId(), "DELETE_NOTICE", "NOTICE", noticeId, "Hide notice");
        return new NoticeMutationResponse(noticeId, "HIDDEN", "Notice hidden");
    }

    private NoticeFilter noticeFilter(String keyword, String status) {
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "PUBLISHED", "SCHEDULED", "HIDDEN"), "notice status", "ALL");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (!"ALL".equals(normalizedStatus)) {
            conditions.add("n.status = ?");
            args.add(normalizedStatus);
        }
        if (normalizedKeyword != null) {
            conditions.add("n.title like ?");
            args.add("%" + normalizedKeyword + "%");
        }

        return new NoticeFilter(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
    }

    private LocalDateTime defaultStartAt(LocalDateTime startAt) {
        return startAt == null ? LocalDateTime.now() : startAt;
    }

    private String resolveStatus(String requestedStatus, LocalDateTime startAt) {
        String normalized = normalizeStatus(requestedStatus, List.of("PUBLISHED", "SCHEDULED", "HIDDEN"), "notice status", null);
        if ("HIDDEN".equals(normalized)) {
            return "HIDDEN";
        }
        return startAt.isAfter(LocalDateTime.now()) ? "SCHEDULED" : "PUBLISHED";
    }

    private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (endAt != null && !startAt.isBefore(endAt)) {
            throw badRequest("startAt must be before endAt");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw badRequest("page must be greater than or equal to 0");
        }
        if (size <= 0 || size > 100) {
            throw badRequest("size must be between 1 and 100");
        }
    }

    private String normalizeStatus(String value, List<String> allowed, String label, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null) {
            return null;
        }
        if (!allowed.contains(normalized)) {
            throw badRequest("Invalid " + label + ": " + value);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureNoticeExists(long noticeId) {
        if (count("select count(*) from notice where id = ?", noticeId) == 0) {
            throw notFound("Notice not found");
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private record NoticeFilter(String whereClause, List<Object> args) {
    }
}
