package com.mock.maesoongan.adminservice.member;

import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberDetailResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberListItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberSearchItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberSummaryResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.PageResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.ReleaseSuspensionRequest;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.ReleaseSuspensionResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentHistoryItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentRequest;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentSummaryResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspendMembersRequest;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspendMembersResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspensionDetailResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspensionHistoryItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspensionSummaryResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

@Service
public class AdminMemberService {

    private static final int DEFAULT_ADMIN_ID = 1;
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yy.MM.dd");

    private final JdbcTemplate jdbcTemplate;
    private final MemberSecurityService memberSecurityService;

    public AdminMemberService(JdbcTemplate jdbcTemplate, MemberSecurityService memberSecurityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberSecurityService = memberSecurityService;
    }

    @Transactional(readOnly = true)
    public MemberSummaryResponse getMemberSummary() {
        LocalDate today = LocalDate.now();
        return new MemberSummaryResponse(
                count("select count(*) from member_snapshot where status <> 'DELETED'"),
                count("select count(*) from member_snapshot where status = 'ACTIVE'"),
                count("select count(*) from member_snapshot where status = 'SUSPENDED'"),
                count("""
                        select count(*)
                        from member_snapshot
                        where status <> 'DELETED' and created_at >= ? and created_at < ?
                        """, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberListItem> getMembers(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size,
            String sort
    ) {
        validatePage(page, size);
        MemberFilter filter = memberFilter(keyword, status, startDate, endDate);

        long total = count("select count(*) from member_snapshot m " + filter.whereClause(), filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add(page * size);

        List<MemberListItem> content = jdbcTemplate.query("""
                        select m.member_id,
                               m.nickname,
                               m.login_id,
                               m.email,
                               date(m.created_at) as join_date,
                               m.login_fail_count,
                               m.status,
                               ps.total_asset,
                               ps.profit_rate,
                               coalesce(cp.contest_count, 0) as contest_count
                        from member_snapshot m
                        left join portfolio_snapshot ps on ps.member_id = m.member_id and ps.contest_id = 0
                        left join (
                            select member_id, count(*) as contest_count
                            from contest_participation
                            group by member_id
                        ) cp on cp.member_id = m.member_id
                        %s
                        %s
                        limit ? offset ?
                        """.formatted(filter.whereClause(), memberOrderBy(sort)),
                (rs, rowNum) -> new MemberListItem(
                        rs.getLong("member_id"),
                        rs.getString("nickname"),
                        rs.getString("login_id"),
                        maskEmail(rs.getString("email")),
                        formatShortDate(rs.getDate("join_date")),
                        rs.getLong("contest_count"),
                        formatAsset(rs.getBigDecimal("total_asset")),
                        rs.getBigDecimal("profit_rate"),
                        rs.getInt("login_fail_count"),
                        rs.getString("status")
                ),
                args.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    // 정렬 컬럼은 허용목록으로만 매핑(SQL 인젝션 방지). 'field,asc|desc' 형식.
    private String memberOrderBy(String sort) {
        String tieBreak = ", m.member_id desc";
        String defaultOrder = "order by m.created_at desc" + tieBreak;
        if (sort == null || sort.isBlank()) {
            return defaultOrder;
        }
        String[] parts = sort.split(",");
        String column = switch (parts[0].trim()) {
            case "joinedAt" -> "m.created_at";
            case "contestCount" -> "contest_count";
            case "totalAsset" -> "ps.total_asset";
            case "profitRate" -> "ps.profit_rate";
            case "loginFailCount" -> "m.login_fail_count";
            default -> null;
        };
        if (column == null) {
            return defaultOrder;
        }
        boolean asc = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim());
        return "order by " + column + (asc ? " asc" : " desc") + tieBreak;
    }

    @Transactional(readOnly = true)
    public List<MemberSearchItem> searchMembers(String keyword, int limit) {
        if (limit <= 0 || limit > 50) {
            throw badRequest("limit must be between 1 and 50");
        }

        String normalized = normalize(keyword);
        List<Object> args = new ArrayList<>();
        String where = "where m.status <> 'DELETED'";

        if (normalized != null) {
            where += " and (m.nickname like ? or m.email like ? or m.login_id like ?)";
            String like = "%" + normalized + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        args.add(limit);
        return jdbcTemplate.query("""
                        select m.member_id, m.nickname, m.login_id, m.email, m.status
                        from member_snapshot m
                        %s
                        order by m.created_at desc, m.member_id desc
                        limit ?
                        """.formatted(where),
                (rs, rowNum) -> new MemberSearchItem(
                        rs.getLong("member_id"),
                        rs.getString("nickname"),
                        rs.getString("login_id"),
                        maskEmail(rs.getString("email")),
                        rs.getString("status")
                ),
                args.toArray());
    }

    @Transactional(readOnly = true)
    public MemberDetailResponse getMember(long memberId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select m.member_id,
                                   m.nickname,
                                   m.login_id,
                                   m.email,
                                   date(m.created_at) as join_date,
                                   m.login_fail_count,
                                   m.status,
                                   ps.total_asset,
                                   ps.profit_rate,
                                   coalesce(cp.contest_count, 0) as contest_count
                            from member_snapshot m
                            left join portfolio_snapshot ps on ps.member_id = m.member_id and ps.contest_id = 0
                            left join (
                                select member_id, count(*) as contest_count
                                from contest_participation
                                group by member_id
                            ) cp on cp.member_id = m.member_id
                            where m.member_id = ? and m.status <> 'DELETED'
                            """,
                    (rs, rowNum) -> new MemberDetailResponse(
                            rs.getLong("member_id"),
                            rs.getString("nickname"),
                            rs.getString("login_id"),
                            rs.getString("email"),
                            rs.getDate("join_date").toLocalDate(),
                            rs.getLong("contest_count"),
                            rs.getBigDecimal("total_asset"),
                            rs.getBigDecimal("profit_rate"),
                            rs.getInt("login_fail_count"),
                            rs.getString("status"),
                            getRecentSuspensions(memberId),
                            getRecentSeedPayments(memberId)
                    ),
                    memberId);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("Member not found");
        }
    }

    @Transactional(readOnly = true)
    public String exportMembers(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        MemberFilter filter = memberFilter(keyword, status, startDate, endDate);
        List<MemberListItem> members = jdbcTemplate.query("""
                        select m.member_id,
                               m.nickname,
                               m.login_id,
                               m.email,
                               date(m.created_at) as join_date,
                               m.login_fail_count,
                               m.status,
                               ps.total_asset,
                               ps.profit_rate,
                               coalesce(cp.contest_count, 0) as contest_count
                        from member_snapshot m
                        left join portfolio_snapshot ps on ps.member_id = m.member_id and ps.contest_id = 0
                        left join (
                            select member_id, count(*) as contest_count
                            from contest_participation
                            group by member_id
                        ) cp on cp.member_id = m.member_id
                        %s
                        order by m.created_at desc, m.member_id desc
                        """.formatted(filter.whereClause()),
                (rs, rowNum) -> new MemberListItem(
                        rs.getLong("member_id"),
                        rs.getString("nickname"),
                        rs.getString("login_id"),
                        rs.getString("email"),
                        formatShortDate(rs.getDate("join_date")),
                        rs.getLong("contest_count"),
                        formatAsset(rs.getBigDecimal("total_asset")),
                        rs.getBigDecimal("profit_rate"),
                        rs.getInt("login_fail_count"),
                        rs.getString("status")
                ),
                filter.args().toArray());

        StringBuilder csv = new StringBuilder("memberId,nickname,accountId,email,joinDate,contestCount,totalAsset,profitRate,loginFailCount,status\n");
        for (MemberListItem member : members) {
            csv.append(csvLine(Arrays.asList(
                    member.memberId(),
                    member.nickname(),
                    member.accountId(),
                    member.email(),
                    member.joinDate(),
                    member.contestCount(),
                    member.totalAsset(),
                    member.profitRate(),
                    member.loginFailCount(),
                    member.status()
            )));
        }
        return csv.toString();
    }

    @Transactional
    public SuspendMembersResponse suspendMembers(SuspendMembersRequest request) {
        List<Long> memberIds = distinctIds(request.memberIds());
        if (memberIds.isEmpty()) {
            throw badRequest("memberIds is required");
        }

        long adminId = currentAdminId();
        List<Long> suspendedIds = new ArrayList<>();
        List<Long> skippedIds = new ArrayList<>();
        List<Long> notFoundIds = new ArrayList<>();

        for (Long memberId : memberIds) {
            MemberStatus member = findMemberStatus(memberId);
            if (member == null) {
                notFoundIds.add(memberId);
                continue;
            }
            if ("SUSPENDED".equals(member.status())) {
                skippedIds.add(memberId);
                continue;
            }

            // AWS DB를 직접 바꾸지 않고 온프렘에 정지 명령 발행.
            // 실제 정지(member_snapshot SUSPENDED, account_suspension)는 온프렘 ADMIN_SUSPENDED 결과 이벤트 수신 시 확정된다.
            memberSecurityService.requestSuspend(memberId, adminId, request.reason());
            suspendedIds.add(memberId);
        }

        // 정지 작업 1회당 감사로그 1건만 기록 (상세 = 정지 대상 닉네임들). 회원별 개별 로그 X
        if (!suspendedIds.isEmpty()) {
            long auditTargetId = suspendedIds.size() == 1 ? suspendedIds.get(0) : 0L;
            insertAudit(adminId, "SUSPEND_MEMBER", "MEMBER", auditTargetId, request.reason());
        }

        return new SuspendMembersResponse(
                memberIds.size(),
                suspendedIds.size(),
                skippedIds.size(),
                notFoundIds.size(),
                suspendedIds,
                skippedIds,
                notFoundIds,
                suspendedIds.size() + "명의 계정이 정지되었습니다."
        );
    }

    @Transactional(readOnly = true)
    public SuspensionSummaryResponse getSuspensionSummary() {
        LocalDate today = LocalDate.now();
        return new SuspensionSummaryResponse(
                count("select count(*) from account_suspension"),
                // 현재 정지중(명): 해제되지 않은 정지의 회원 수 (active 값은 'SUSPENDED'/legacy 'ACTIVE' 모두 포함)
                count("select count(distinct member_id) from account_suspension where status <> 'RELEASED'"),
                count("select count(*) from account_suspension where status = 'RELEASED'"),
                count("""
                        select count(*)
                        from account_suspension
                        where created_at >= ? and created_at < ?
                        """, today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                // 자동 정지(로그인 실패): 자동 정지 사유로 현재 정지중인 건수
                count("select count(*) from account_suspension where status <> 'RELEASED' and reason = ?",
                        "로그인 실패 누적 자동 정지")
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SuspensionHistoryItem> getSuspensions(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size,
            String sort
    ) {
        validatePage(page, size);
        HistoryFilter filter = suspensionFilter(keyword, status, startDate, endDate);

        long total = count("""
                select count(*)
                from account_suspension s
                join member_snapshot m on m.member_id = s.member_id
                %s
                """.formatted(filter.whereClause()), filter.args().toArray());

        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add(page * size);
        List<SuspensionHistoryItem> content = querySuspensionHistory(filter.whereClause(), suspensionOrderBy(sort), args, "limit ? offset ?");

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public SuspensionDetailResponse getSuspension(long suspensionId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select s.id,
                                   s.member_id,
                                   m.nickname,
                                   m.login_id,
                                   m.email,
                                   s.reason,
                                   s.status,
                                   s.admin_id,
                                   coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                                   s.created_at,
                                   s.updated_at,
                                   s.release_admin_id,
                                   coalesce(ra.nickname, ra.login_id) as release_admin_name,
                                   s.released_at
                            from account_suspension s
                            join member_snapshot m on m.member_id = s.member_id
                            left join admin a on a.id = s.admin_id
                            left join admin ra on ra.id = s.release_admin_id
                            where s.id = ?
                            """,
                    (rs, rowNum) -> new SuspensionDetailResponse(
                            rs.getLong("id"),
                            rs.getLong("member_id"),
                            rs.getString("nickname"),
                            rs.getString("login_id"),
                            rs.getString("email"),
                            rs.getString("reason"),
                            rs.getString("status"),
                            rs.getObject("admin_id", Long.class),
                            rs.getString("admin_name"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at")),
                            rs.getObject("release_admin_id", Long.class),
                            rs.getString("release_admin_name"),
                            toLocalDateTime(rs.getTimestamp("released_at"))
                    ),
                    suspensionId);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("Suspension not found");
        }
    }

    @Transactional
    public ReleaseSuspensionResponse releaseSuspension(long suspensionId, ReleaseSuspensionRequest request) {
        SuspensionRow suspension = findSuspensionRow(suspensionId);
        if (suspension == null) {
            throw notFound("Suspension not found");
        }
        if (!"SUSPENDED".equals(suspension.status())) {
            throw badRequest("Suspension is already processed");
        }

        long adminId = currentAdminId();

        // AWS DB를 직접 바꾸지 않고 온프렘에 해제 명령 발행.
        // 실제 해제(account_suspension RELEASED, member_snapshot ACTIVE, login_fail_count 0)는
        // 온프렘 LOCK_RELEASED 결과 이벤트 수신 시 확정된다.
        memberSecurityService.requestRelease(suspension.memberId(), adminId, request.reason());

        insertAudit(adminId, "RELEASE_MEMBER", "MEMBER", suspension.memberId(), request.reason());
        return new ReleaseSuspensionResponse(
                suspensionId,
                suspension.memberId(),
                "RELEASE_REQUESTED",
                "SUSPENDED",
                "정지 해제를 요청했습니다. 처리 완료 후 반영됩니다."
        );
    }

    @Transactional(readOnly = true)
    public String exportSuspensions(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        HistoryFilter filter = suspensionFilter(keyword, status, startDate, endDate);
        List<SuspensionHistoryItem> rows = querySuspensionHistory(filter.whereClause(), suspensionOrderBy(null), filter.args(), "");
        StringBuilder csv = new StringBuilder("suspensionId,memberId,nickname,accountId,reason,status,adminId,adminName,createdAt,releasedAt\n");
        for (SuspensionHistoryItem row : rows) {
            csv.append(csvLine(Arrays.asList(
                    row.suspensionId(),
                    row.memberId(),
                    row.nickname(),
                    row.accountId(),
                    row.reason(),
                    row.status(),
                    row.adminId(),
                    row.adminName(),
                    row.createdAt(),
                    row.releasedAt()
            )));
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public SeedPaymentSummaryResponse getSeedPaymentSummary() {
        LocalDate today = LocalDate.now();
        BigDecimal totalAmount = sum("""
                select coalesce(sum(amount), 0)
                from seed_history
                where request_status <> 'FAILED'
                """);
        BigDecimal todayAmount = sum("""
                select coalesce(sum(amount), 0)
                from seed_history
                where request_status <> 'FAILED'
                  and created_at >= ? and created_at < ?
                """, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        BigDecimal monthAmount = sum("""
                select coalesce(sum(amount), 0)
                from seed_history
                where request_status <> 'FAILED'
                  and created_at >= ? and created_at < ?
                """, monthStart, monthEnd);

        return new SeedPaymentSummaryResponse(
                totalAmount,
                todayAmount,
                count("select count(*) from seed_history where request_status <> 'FAILED'"),
                count("""
                        select count(*)
                        from seed_history
                        where request_status <> 'FAILED'
                          and created_at >= ? and created_at < ?
                        """, today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                monthAmount,
                count("""
                        select count(*)
                        from seed_history
                        where request_status <> 'FAILED'
                          and created_at >= ? and created_at < ?
                        """, monthStart, monthEnd)
        );
    }

    @Transactional
    public SeedPaymentResponse paySeedMoney(SeedPaymentRequest request) {
        List<Long> memberIds = distinctIds(request.memberIds());
        if (memberIds.isEmpty()) {
            throw badRequest("memberIds is required");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("amount must be greater than 0");
        }

        long contestId = resolveContestId(request.contestId());
        long adminId = currentAdminId();
        List<Long> succeededIds = new ArrayList<>();
        List<Long> notFoundIds = new ArrayList<>();

        for (Long memberId : memberIds) {
            MemberStatus member = findMemberStatus(memberId);
            if (member == null) {
                notFoundIds.add(memberId);
                continue;
            }

            upsertPortfolio(memberId, contestId, request.amount());
            jdbcTemplate.update("""
                    insert into seed_history (member_id, admin_id, contest_id, amount, reason, request_status, created_at, processed_at)
                    values (?, ?, ?, ?, ?, 'SUCCESS', ?, ?)
                    """, memberId, adminId, contestId, request.amount(), request.reason(),
                    LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")), LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));

            insertAudit(adminId, "PAY_SEED_MONEY", "MEMBER", memberId, request.reason());

            // 지급받은 회원에게 알림(설정 토글과 무관하게 항상 발송). best-effort.
            try {
                String body = String.format(
                        "시드머니 %,d원이 지급되었습니다.\n사유: %s",
                        request.amount().longValue(),
                        request.reason() == null ? "" : request.reason());
                jdbcTemplate.update("""
                        insert into notification
                            (member_id, type, title, body, is_read, target_type, target_id, delivery_status, retry_count, created_at)
                        values (?, 'SEED_PAYMENT', '시드머니 지급', ?, 0, null, null, 'CREATED', 0, ?)
                        """, memberId, body, LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
            } catch (RuntimeException ignored) {
                // 알림 실패가 지급 처리를 막지 않도록 무시
            }

            succeededIds.add(memberId);
        }

        return new SeedPaymentResponse(
                memberIds.size(),
                succeededIds.size(),
                notFoundIds.size(),
                succeededIds,
                notFoundIds,
                contestId,
                request.amount(),
                succeededIds.size() + "명에게 시드머니가 지급되었습니다."
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SeedPaymentHistoryItem> getSeedPayments(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size,
            String sort
    ) {
        validatePage(page, size);
        HistoryFilter filter = seedFilter(keyword, status, startDate, endDate);

        long total = count("""
                select count(*)
                from seed_history sh
                join member_snapshot m on m.member_id = sh.member_id
                %s
                """.formatted(filter.whereClause()), filter.args().toArray());

        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add(page * size);
        List<SeedPaymentHistoryItem> content = querySeedPaymentHistory(filter.whereClause(), seedOrderBy(sort), args, "limit ? offset ?");

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public String exportSeedPayments(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        HistoryFilter filter = seedFilter(keyword, status, startDate, endDate);
        List<SeedPaymentHistoryItem> rows = querySeedPaymentHistory(filter.whereClause(), seedOrderBy(null), filter.args(), "");
        StringBuilder csv = new StringBuilder("seedHistoryId,memberId,nickname,accountId,contestId,amount,reason,requestStatus,adminId,adminName,createdAt,processedAt\n");
        for (SeedPaymentHistoryItem row : rows) {
            csv.append(csvLine(Arrays.asList(
                    row.seedHistoryId(),
                    row.memberId(),
                    row.nickname(),
                    row.accountId(),
                    row.contestId(),
                    row.amount(),
                    row.reason(),
                    row.requestStatus(),
                    row.adminId(),
                    row.adminName(),
                    row.createdAt(),
                    row.processedAt()
            )));
        }
        return csv.toString();
    }

    private List<SuspensionHistoryItem> getRecentSuspensions(long memberId) {
        return jdbcTemplate.query("""
                        select s.id,
                               s.member_id,
                               m.nickname,
                               m.login_id,
                               s.reason,
                               s.status,
                               s.admin_id,
                               coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                               s.created_at,
                               s.released_at
                        from account_suspension s
                        join member_snapshot m on m.member_id = s.member_id
                        left join admin a on a.id = s.admin_id
                        where s.member_id = ?
                        order by s.created_at desc, s.id desc
                        limit 5
                        """,
                this::mapSuspensionHistory,
                memberId);
    }

    private List<SeedPaymentHistoryItem> getRecentSeedPayments(long memberId) {
        return jdbcTemplate.query("""
                        select sh.id,
                               sh.member_id,
                               m.nickname,
                               m.login_id,
                               sh.contest_id,
                               sh.amount,
                               sh.reason,
                               sh.request_status,
                               sh.admin_id,
                               coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                               sh.created_at,
                               sh.processed_at
                        from seed_history sh
                        join member_snapshot m on m.member_id = sh.member_id
                        left join admin a on a.id = sh.admin_id
                        where sh.member_id = ?
                        order by sh.created_at desc, sh.id desc
                        limit 5
                        """,
                this::mapSeedPaymentHistory,
                memberId);
    }

    private List<SuspensionHistoryItem> querySuspensionHistory(String whereClause, String orderBy, List<Object> args, String suffix) {
        return jdbcTemplate.query("""
                        select s.id,
                               s.member_id,
                               m.nickname,
                               m.login_id,
                               s.reason,
                               s.status,
                               s.admin_id,
                               coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                               s.created_at,
                               s.released_at
                        from account_suspension s
                        join member_snapshot m on m.member_id = s.member_id
                        left join admin a on a.id = s.admin_id
                        %s
                        %s
                        %s
                        """.formatted(whereClause, orderBy, suffix),
                this::mapSuspensionHistory,
                args.toArray());
    }

    // 정지내역 정렬(허용목록). 'processedAt|createdAt,asc|desc'
    private String suspensionOrderBy(String sort) {
        String tie = ", s.id desc";
        if (sort == null || sort.isBlank()) {
            return "order by s.created_at desc" + tie;
        }
        String[] parts = sort.split(",");
        String column = switch (parts[0].trim()) {
            case "processedAt", "createdAt" -> "s.created_at";
            default -> null;
        };
        if (column == null) {
            return "order by s.created_at desc" + tie;
        }
        boolean asc = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim());
        return "order by " + column + (asc ? " asc" : " desc") + tie;
    }

    private List<SeedPaymentHistoryItem> querySeedPaymentHistory(String whereClause, String orderBy, List<Object> args, String suffix) {
        return jdbcTemplate.query("""
                        select sh.id,
                               sh.member_id,
                               m.nickname,
                               m.login_id,
                               sh.contest_id,
                               sh.amount,
                               sh.reason,
                               sh.request_status,
                               sh.admin_id,
                               coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                               sh.created_at,
                               sh.processed_at
                        from seed_history sh
                        join member_snapshot m on m.member_id = sh.member_id
                        left join admin a on a.id = sh.admin_id
                        %s
                        %s
                        %s
                        """.formatted(whereClause, orderBy, suffix),
                this::mapSeedPaymentHistory,
                args.toArray());
    }

    // 시드내역 정렬(허용목록). 'amount|paidAt|createdAt,asc|desc'
    private String seedOrderBy(String sort) {
        String tie = ", sh.id desc";
        if (sort == null || sort.isBlank()) {
            return "order by sh.created_at desc" + tie;
        }
        String[] parts = sort.split(",");
        String column = switch (parts[0].trim()) {
            case "amount" -> "sh.amount";
            case "paidAt", "createdAt" -> "sh.created_at";
            default -> null;
        };
        if (column == null) {
            return "order by sh.created_at desc" + tie;
        }
        boolean asc = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim());
        return "order by " + column + (asc ? " asc" : " desc") + tie;
    }

    private SuspensionHistoryItem mapSuspensionHistory(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SuspensionHistoryItem(
                rs.getLong("id"),
                rs.getLong("member_id"),
                rs.getString("nickname"),
                rs.getString("login_id"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getObject("admin_id", Long.class),
                rs.getString("admin_name"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("released_at"))
        );
    }

    private SeedPaymentHistoryItem mapSeedPaymentHistory(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SeedPaymentHistoryItem(
                rs.getLong("id"),
                rs.getLong("member_id"),
                rs.getString("nickname"),
                rs.getString("login_id"),
                rs.getObject("contest_id", Long.class),
                rs.getBigDecimal("amount"),
                rs.getString("reason"),
                rs.getString("request_status"),
                rs.getObject("admin_id", Long.class),
                rs.getString("admin_name"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("processed_at"))
        );
    }

    private MemberFilter memberFilter(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "ACTIVE", "SUSPENDED", "TODAY"), "member status");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if ("TODAY".equals(normalizedStatus)) {
            LocalDate today = LocalDate.now();
            conditions.add("m.status <> 'DELETED'");
            conditions.add("m.created_at >= ? and m.created_at < ?");
            args.add(today.atStartOfDay());
            args.add(today.plusDays(1).atStartOfDay());
        } else if ("ALL".equals(normalizedStatus)) {
            conditions.add("m.status <> 'DELETED'");
        } else {
            conditions.add("m.status = ?");
            args.add(normalizedStatus);
        }

        addKeywordCondition(conditions, args, normalizedKeyword, "m");
        addDateCondition(conditions, args, startDate, endDate, "m.created_at");
        return new MemberFilter(where(conditions), args);
    }

    private HistoryFilter suspensionFilter(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "SUSPENDED", "RELEASED"), "suspension status");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (!"ALL".equals(normalizedStatus)) {
            conditions.add("s.status = ?");
            args.add(normalizedStatus);
        }
        addKeywordCondition(conditions, args, normalizedKeyword, "m");
        addDateCondition(conditions, args, startDate, endDate, "s.created_at");
        return new HistoryFilter(where(conditions), args);
    }

    private HistoryFilter seedFilter(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "REQUESTED", "SUCCESS", "FAILED"), "seed payment status");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (!"ALL".equals(normalizedStatus)) {
            conditions.add("sh.request_status = ?");
            args.add(normalizedStatus);
        }
        addKeywordCondition(conditions, args, normalizedKeyword, "m");
        addDateCondition(conditions, args, startDate, endDate, "sh.created_at");
        return new HistoryFilter(where(conditions), args);
    }

    private void addKeywordCondition(List<String> conditions, List<Object> args, String keyword, String alias) {
        if (keyword == null) {
            return;
        }
        conditions.add("(" + alias + ".nickname like ? or " + alias + ".email like ? or " + alias + ".login_id like ?)");
        String like = "%" + keyword + "%";
        args.add(like);
        args.add(like);
        args.add(like);
    }

    private void addDateCondition(List<String> conditions, List<Object> args, LocalDate startDate, LocalDate endDate, String column) {
        if (startDate != null) {
            conditions.add(column + " >= ?");
            args.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            conditions.add(column + " < ?");
            args.add(endDate.plusDays(1).atStartOfDay());
        }
    }

    private String where(List<String> conditions) {
        return conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
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

    private String normalizeStatus(String value, List<String> allowed, String label) {
        String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw badRequest("Invalid " + label + ": " + value);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<Long> distinctIds(List<Long> ids) {
        Set<Long> values = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0) {
                    values.add(id);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private MemberStatus findMemberStatus(long memberId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select member_id, status
                            from member_snapshot
                            where member_id = ? and status <> 'DELETED'
                            """,
                    (rs, rowNum) -> new MemberStatus(rs.getLong("member_id"), rs.getString("status")),
                    memberId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private SuspensionRow findSuspensionRow(long suspensionId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select id, member_id, status
                            from account_suspension
                            where id = ?
                            """,
                    (rs, rowNum) -> new SuspensionRow(rs.getLong("id"), rs.getLong("member_id"), rs.getString("status")),
                    suspensionId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void upsertPortfolio(long memberId, long contestId, BigDecimal amount) {
        int updated = jdbcTemplate.update("""
                update portfolio_snapshot
                set cash_balance = cash_balance + ?,
                    available_cash = available_cash + ?,
                    total_asset = total_asset + ?,
                    portfolio_version = portfolio_version + 1,
                    synced_at = ?
                where member_id = ? and contest_id = ?
                """, amount, amount, amount, LocalDateTime.now(), memberId, contestId);

        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into portfolio_snapshot (
                        member_id,
                        contest_id,
                        cash_balance,
                        available_cash,
                        stock_evaluation_amount,
                        total_asset,
                        total_buy_amount,
                        total_sell_amount,
                        profit_amount,
                        profit_rate,
                        portfolio_version,
                        synced_at
                    )
                    values (?, ?, ?, ?, 0, ?, 0, 0, 0, 0, 1, ?)
                    """, memberId, contestId, amount, amount, amount, LocalDateTime.now());
        }
    }

    private long resolveContestId(Long contestId) {
        long resolved = contestId == null ? 0L : contestId;
        if (resolved < 0) {
            throw badRequest("contestId must be greater than or equal to 0");
        }
        if (resolved > 0 && count("select count(*) from contest where id = ?", resolved) == 0) {
            throw notFound("Contest not found");
        }
        return resolved;
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
                """, adminId, action, targetType, targetId, reason, LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal sum(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private int totalPages(long total, int size) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        int visible = Math.min(3, local.length());
        return local.substring(0, visible) + "***" + domain;
    }

    private String formatShortDate(Date date) {
        return date == null ? "" : SHORT_DATE_FORMATTER.format(date.toLocalDate());
    }

    private String formatAsset(BigDecimal asset) {
        if (asset == null) {
            return null;
        }
        BigDecimal million = BigDecimal.valueOf(1_000_000L);
        if (asset.abs().compareTo(million) >= 0) {
            BigDecimal value = asset.divide(million, 1, RoundingMode.HALF_UP);
            String text = String.format(Locale.US, "%.1fM원", value);
            return text.replace(".0M", "M");
        }
        return asset.setScale(0, RoundingMode.HALF_UP).toPlainString() + "원";
    }

    private String csvLine(List<Object> values) {
        StringJoiner joiner = new StringJoiner(",");
        for (Object value : values) {
            joiner.add(csvValue(value));
        }
        return joiner + "\n";
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
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

    private record MemberFilter(String whereClause, List<Object> args) {
    }

    private record HistoryFilter(String whereClause, List<Object> args) {
    }

    private record MemberStatus(long memberId, String status) {
    }

    private record SuspensionRow(long suspensionId, long memberId, String status) {
    }
}
