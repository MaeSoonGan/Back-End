package com.mock.maesoongan.admin.member;

import com.mock.maesoongan.admin.member.AdminMemberDtos.AddMemberRequest;
import com.mock.maesoongan.admin.member.AdminMemberDtos.AddMemberResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSeedMoneyResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSuspendResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberDetailResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberListItem;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberPageResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberSummaryResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.SeedMoneyResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.SuspendMemberResponse;
import com.mock.maesoongan.common.BusinessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AdminMemberService {

    private static final long ADMIN_ID = 1L;
    private static final DateTimeFormatter LIST_DATE_FORMATTER = DateTimeFormatter.ofPattern("yy.MM.dd");
    private static final DateTimeFormatter DETAIL_DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final List<String> STATUSES = List.of("ALL", "ACTIVE", "SUSPENDED", "TODAY");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminMemberService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public MemberSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        return new MemberSummaryResponse(
                toInt(count("select count(*) from member_snapshot where status <> 'DELETED'")),
                toInt(count("select count(*) from member_snapshot where status = 'ACTIVE'")),
                toInt(count("select count(*) from member_snapshot where status = 'SUSPENDED'")),
                toInt(count("""
                        select count(*)
                        from member_snapshot
                        where created_at >= ? and created_at < ? and status <> 'DELETED'
                        """, today.atStartOfDay(), today.plusDays(1).atStartOfDay()))
        );
    }

    public MemberPageResponse getMembers(String keyword, String status, LocalDate startDate, LocalDate endDate,
                                         int page, int size) {
        validatePage(page, size);
        Filter filter = buildFilter(keyword, status, startDate, endDate);

        long total = count("select count(*) from member_snapshot ms where " + filter.whereClause(), filter.params().toArray());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);

        List<Object> params = new ArrayList<>(filter.params());
        params.add(size);
        params.add(page * size);
        List<MemberListItem> content = jdbcTemplate.query("""
                        select ms.member_id,
                               ms.nickname,
                               ms.login_id,
                               ms.email,
                               ms.created_at,
                               ms.login_fail_count,
                               ms.status,
                               coalesce(cp.contest_count, 0) as contest_count,
                               ps.total_asset,
                               r.profit_rate
                        from member_snapshot ms
                        left join (
                            select member_id, count(*) as contest_count
                            from contest_participation
                            group by member_id
                        ) cp on cp.member_id = ms.member_id
                        left join portfolio_snapshot ps on ps.member_id = ms.member_id and ps.contest_id = 0
                        left join (
                            select member_id, avg(profit_rate) as profit_rate
                            from ranking
                            where profit_rate is not null and is_excluded = false
                            group by member_id
                        ) r on r.member_id = ms.member_id
                        where %s
                        order by ms.member_id asc
                        limit ? offset ?
                        """.formatted(filter.whereClause()),
                (rs, rowNum) -> new MemberListItem(
                        rs.getLong("member_id"),
                        rs.getString("nickname"),
                        rs.getString("login_id"),
                        maskEmail(rs.getString("email")),
                        LIST_DATE_FORMATTER.format(toLocalDateTime(rs.getTimestamp("created_at"))),
                        rs.getInt("contest_count"),
                        formatAsset(rs.getBigDecimal("total_asset")),
                        toRoundedDouble(rs.getBigDecimal("profit_rate")),
                        rs.getInt("login_fail_count"),
                        rs.getString("status")
                ),
                params.toArray());

        return new MemberPageResponse(content, total, totalPages, page);
    }

    public MemberDetailResponse getMember(long userId) {
        return jdbcTemplate.queryForObject("""
                        select ms.member_id,
                               ms.nickname,
                               ms.login_id,
                               ms.email,
                               ms.created_at,
                               ms.login_fail_count,
                               ms.status,
                               coalesce(cp.contest_count, 0) as contest_count,
                               ps.total_asset,
                               r.profit_rate
                        from member_snapshot ms
                        left join (
                            select member_id, count(*) as contest_count
                            from contest_participation
                            group by member_id
                        ) cp on cp.member_id = ms.member_id
                        left join portfolio_snapshot ps on ps.member_id = ms.member_id and ps.contest_id = 0
                        left join (
                            select member_id, avg(profit_rate) as profit_rate
                            from ranking
                            where profit_rate is not null and is_excluded = false
                            group by member_id
                        ) r on r.member_id = ms.member_id
                        where ms.member_id = ?
                        """,
                (rs, rowNum) -> new MemberDetailResponse(
                        rs.getLong("member_id"),
                        rs.getString("nickname"),
                        rs.getString("login_id"),
                        rs.getString("email"),
                        DETAIL_DATE_FORMATTER.format(toLocalDateTime(rs.getTimestamp("created_at")).toLocalDate()),
                        rs.getInt("contest_count"),
                        toLong(rs.getBigDecimal("total_asset")),
                        toRoundedDouble(rs.getBigDecimal("profit_rate")),
                        rs.getInt("login_fail_count"),
                        rs.getString("status")
                ),
                userId);
    }

    @Transactional
    public AddMemberResponse addMember(AddMemberRequest request) {
        if (exists("select count(*) from member_snapshot where email = ?", request.email())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다.");
        }

        long userId = nextId("member_snapshot", "member_id");
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        insert into member_snapshot
                        (member_id, login_id, email, nickname, phone, status, login_fail_count, email_verified,
                         profile_image_url, created_at, updated_at, synced_at)
                        values (?, ?, ?, ?, null, 'ACTIVE', 0, true, null, ?, null, ?)
                        """,
                userId,
                "user" + String.format("%04d", userId),
                request.email(),
                request.nickname(),
                now,
                now);
        jdbcTemplate.update("""
                        insert into dev_member_auth
                        (member_id, password_hash, password_updated_at, login_fail_count, locked_until, created_at, updated_at)
                        values (?, ?, ?, 0, null, ?, null)
                        """,
                userId,
                passwordEncoder.encode(request.password()),
                now,
                now);
        insertAuditLog("CREATE_MEMBER", "MEMBER", userId, "관리자 회원 직접 추가", now);

        return new AddMemberResponse(userId, "회원이 추가되었습니다.");
    }

    public byte[] exportCsv(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("nickname,accountId,email,joinDate,contestCount,totalAsset,profitRate,loginFailCount,status\n");

        int page = 0;
        MemberPageResponse response;
        do {
            response = getMembers(keyword, status, startDate, endDate, page++, 500);
            for (MemberListItem member : response.content()) {
                csv.append(escape(member.nickname())).append(',')
                        .append(escape(member.accountId())).append(',')
                        .append(escape(member.email())).append(',')
                        .append(member.joinDate()).append(',')
                        .append(member.contestCount()).append(',')
                        .append(member.totalAsset() == null ? "" : member.totalAsset()).append(',')
                        .append(member.profitRate() == null ? "" : member.profitRate()).append(',')
                        .append(member.loginFailCount()).append(',')
                        .append(member.status()).append('\n');
            }
        } while (page < response.totalPages());

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public SuspendMemberResponse suspendMember(long userId) {
        ensureMemberExists(userId);
        jdbcTemplate.update("update member_snapshot set status = 'SUSPENDED', updated_at = ? where member_id = ?", LocalDateTime.now(), userId);
        saveSuspension(userId, "관리자 회원 관리 화면 계정 정지");
        insertAuditLog("SUSPEND_MEMBER", "MEMBER", userId, "관리자 회원 관리 화면 계정 정지", LocalDateTime.now());
        return new SuspendMemberResponse(userId, "SUSPENDED", "계정이 정지되었습니다.");
    }

    @Transactional
    public BatchSuspendResponse suspendMembers(List<Long> userIds) {
        validateIds(userIds);

        int count = 0;
        for (Long userId : userIds) {
            if (exists("select count(*) from member_snapshot where member_id = ?", userId)) {
                jdbcTemplate.update("update member_snapshot set status = 'SUSPENDED', updated_at = ? where member_id = ?", LocalDateTime.now(), userId);
                saveSuspension(userId, "관리자 회원 관리 화면 일괄 계정 정지");
                insertAuditLog("SUSPEND_MEMBER", "MEMBER", userId, "관리자 회원 관리 화면 일괄 계정 정지", LocalDateTime.now());
                count++;
            }
        }

        return new BatchSuspendResponse(count, count + "명의 계정이 정지되었습니다.");
    }

    @Transactional
    public SeedMoneyResponse paySeedMoney(long userId, long seedAmount) {
        validateSeedAmount(seedAmount);
        ensureMemberExists(userId);
        paySeed(userId, seedAmount);
        return new SeedMoneyResponse(userId, seedAmount, "시드머니가 지급되었습니다.");
    }

    @Transactional
    public BatchSeedMoneyResponse paySeedMoneyToMembers(List<Long> userIds, long seedAmount) {
        validateIds(userIds);
        validateSeedAmount(seedAmount);

        int count = 0;
        for (Long userId : userIds) {
            if (exists("select count(*) from member_snapshot where member_id = ?", userId)) {
                paySeed(userId, seedAmount);
                count++;
            }
        }

        return new BatchSeedMoneyResponse(count, count + "명에게 시드머니가 지급되었습니다.");
    }

    private void paySeed(long userId, long seedAmount) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal amount = BigDecimal.valueOf(seedAmount);
        long portfolioId = queryForLong("select id from portfolio_snapshot where member_id = ? and contest_id = 0", userId);
        if (portfolioId == 0) {
            jdbcTemplate.update("""
                            insert into portfolio_snapshot
                            (id, member_id, contest_id, cash_balance, available_cash, stock_evaluation_amount,
                             total_asset, total_buy_amount, total_sell_amount, profit_amount, profit_rate,
                             holdings_json, portfolio_version, onprem_updated_at, synced_at)
                            values (?, ?, 0, ?, ?, 0, ?, 0, 0, 0, 0, null, 1, ?, ?)
                            """,
                    nextId("portfolio_snapshot", "id"), userId, amount, amount, amount, now, now);
        } else {
            jdbcTemplate.update("""
                            update portfolio_snapshot
                            set cash_balance = cash_balance + ?,
                                available_cash = available_cash + ?,
                                total_asset = total_asset + ?,
                                portfolio_version = portfolio_version + 1,
                                synced_at = ?
                            where id = ?
                            """,
                    amount, amount, amount, now, portfolioId);
        }

        jdbcTemplate.update("""
                        insert into seed_history
                        (id, member_id, admin_id, contest_id, amount, reason, request_status, failure_reason, created_at, processed_at)
                        values (?, ?, ?, 0, ?, '관리자 시드머니 지급', 'SUCCESS', null, ?, ?)
                        """,
                nextId("seed_history", "id"), userId, ADMIN_ID, amount, now, now);
        insertAuditLog("SEED_PAYMENT", "MEMBER", userId, "관리자 시드머니 지급", now);
    }

    private Filter buildFilter(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        validateStatus(status);
        validateDateRange(startDate, endDate);

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        conditions.add("ms.status <> 'DELETED'");

        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(lower(ms.nickname) like ? or lower(ms.email) like ? or lower(ms.login_id) like ?)");
            String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(value);
            params.add(value);
            params.add(value);
        }
        if ("TODAY".equals(status)) {
            conditions.add("ms.created_at >= ? and ms.created_at < ?");
            params.add(LocalDate.now().atStartOfDay());
            params.add(LocalDate.now().plusDays(1).atStartOfDay());
        } else if (status != null && !status.isBlank() && !"ALL".equals(status)) {
            conditions.add("ms.status = ?");
            params.add(status);
        }
        if (startDate != null) {
            conditions.add("ms.created_at >= ?");
            params.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            conditions.add("ms.created_at < ?");
            params.add(endDate.plusDays(1).atStartOfDay());
        }

        return new Filter(String.join(" and ", conditions), params);
    }

    private void ensureMemberExists(long userId) {
        if (!exists("select count(*) from member_snapshot where member_id = ?", userId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다.");
        }
    }

    private void saveSuspension(long userId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        insert into account_suspension
                        (id, member_id, admin_id, reason, status, suspended_until, released_at, release_admin_id, created_at, updated_at)
                        values (?, ?, ?, ?, 'SUSPENDED', null, null, null, ?, null)
                        """,
                nextId("account_suspension", "id"), userId, ADMIN_ID, reason, now);
    }

    private void insertAuditLog(String action, String targetType, long targetId, String reason, LocalDateTime now) {
        jdbcTemplate.update("""
                        insert into audit_log
                        (id, admin_id, action, target_type, target_id, reason, result, ip_address, user_agent, created_at)
                        values (?, ?, ?, ?, ?, ?, 'SUCCESS', null, 'swagger', ?)
                        """,
                nextId("audit_log", "id"), ADMIN_ID, action, targetType, targetId, reason, now);
    }

    private boolean exists(String sql, Object... args) {
        return count(sql, args) > 0;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long queryForLong(String sql, Object... args) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
            return value == null ? 0 : value;
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    private long nextId(String tableName, String idColumn) {
        return jdbcTemplate.queryForObject("select coalesce(max(" + idColumn + "), 0) + 1 from " + tableName, Long.class);
    }

    private void validateStatus(String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "잘못된 회원 상태입니다.");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "가입일 조회 범위가 올바르지 않습니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "페이지 요청 값이 올바르지 않습니다.");
        }
    }

    private void validateIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_USER_IDS", "회원 ID 목록은 필수입니다.");
        }
    }

    private void validateSeedAmount(long seedAmount) {
        if (seedAmount <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SEED_AMOUNT", "시드머니 금액이 올바르지 않습니다.");
        }
    }

    private int toInt(long value) {
        return Math.toIntExact(value);
    }

    private Long toLong(BigDecimal value) {
        return value == null ? null : value.longValue();
    }

    private Double toRoundedDouble(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return Math.round(value.doubleValue() * 10) / 10.0;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, Math.min(4, atIndex)) + "***" + email.substring(atIndex);
    }

    private String formatAsset(BigDecimal totalAsset) {
        if (totalAsset == null) {
            return null;
        }
        if (totalAsset.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            double value = Math.round((totalAsset.doubleValue() / 1_000_000.0) * 10) / 10.0;
            return value + "M원";
        }
        return totalAsset.longValue() + "원";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private record Filter(String whereClause, List<Object> params) {
    }
}
