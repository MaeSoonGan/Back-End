package com.mock.maesoongan.adminservice.contest;

import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestCreateRequest;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestDetailResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestListItem;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestMutationResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestResultResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestSummaryResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.ContestUpdateRequest;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.PageResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingExcludeRequest;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingItem;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingStatsResponse;
import com.mock.maesoongan.adminservice.contest.AdminContestDtos.RankingStatusResponse;
import com.mock.maesoongan.adminservice.contest.ContestEvents.ContestEvent;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

@Service
public class AdminContestService {

    private static final int DEFAULT_ADMIN_ID = 1;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final JdbcTemplate jdbcTemplate;
    private final ContestEventPublisher contestEventPublisher;

    public AdminContestService(JdbcTemplate jdbcTemplate, ContestEventPublisher contestEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.contestEventPublisher = contestEventPublisher;
    }

    @Transactional(readOnly = true)
    public ContestSummaryResponse getContestSummary() {
        return new ContestSummaryResponse(
                count("select count(*) from contest"),
                count("select count(*) from contest where status = 'SCHEDULED'"),
                count("select count(*) from contest where status in ('ACTIVE', 'CLOSING_SOON')"),
                count("select count(*) from contest where status = 'ENDED'"),
                count("select count(*) from contest where status = 'CANCELED'"),
                count("select count(*) from contest_participation where status = 'ACTIVE'")
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ContestListItem> getContests(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        validatePage(page, size);
        ContestFilter filter = contestFilter(keyword, status, startDate, endDate);

        long total = count("select count(*) from contest c " + filter.whereClause(), filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add(page * size);

        List<ContestListItem> content = jdbcTemplate.query("""
                        select c.id,
                               c.title,
                               c.seed_money,
                               c.max_participants,
                               c.is_public,
                               c.start_at,
                               c.end_at,
                               c.status,
                               coalesce(cp.participant_count, 0) as participant_count
                        from contest c
                        left join (
                            select contest_id, count(*) as participant_count
                            from contest_participation
                            where status = 'ACTIVE'
                            group by contest_id
                        ) cp on cp.contest_id = c.id
                        %s
                        order by c.created_at desc, c.id desc
                        limit ? offset ?
                        """.formatted(filter.whereClause()),
                (rs, rowNum) -> new ContestListItem(
                        rs.getLong("id"),
                        rs.getString("title"),
                        period(rs.getTimestamp("start_at"), rs.getTimestamp("end_at")),
                        rs.getBigDecimal("seed_money"),
                        rs.getObject("max_participants", Integer.class),
                        rs.getLong("participant_count"),
                        rs.getString("status"),
                        rs.getBoolean("is_public"),
                        toLocalDateTime(rs.getTimestamp("start_at")),
                        toLocalDateTime(rs.getTimestamp("end_at"))
                ),
                args.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional
    public ContestMutationResponse createContest(ContestCreateRequest request) {
        validateContestTimes(request.startAt(), request.endAt());
        String status = normalizeStatus(request.status(), List.of("SCHEDULED", "ACTIVE", "CLOSING_SOON", "ENDED", "CANCELED"), "contest status", "SCHEDULED");
        String profitCriteria = normalizeStatus(request.profitCriteria(), List.of("RATE", "AMOUNT"), "profit criteria", "RATE");
        String joinType = normalizeStatus(request.joinType(), List.of("ALL", "INVITE"), "join type", "ALL");
        boolean isPublic = request.isPublic() == null || request.isPublic();
        long adminId = currentAdminId();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into contest (
                        admin_id,
                        title,
                        description,
                        seed_money,
                        max_participants,
                        max_order_amount,
                        max_stock_ratio,
                        stock_type,
                        profit_criteria,
                        is_public,
                        join_type,
                        start_at,
                        end_at,
                        status,
                        created_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, adminId);
            ps.setString(2, request.title());
            ps.setString(3, request.description());
            ps.setBigDecimal(4, request.seedMoney());
            setInteger(ps, 5, request.maxParticipants());
            ps.setBigDecimal(6, request.maxOrderAmount());
            ps.setBigDecimal(7, request.maxStockRatio());
            ps.setString(8, request.stockType());
            ps.setString(9, profitCriteria);
            ps.setBoolean(10, isPublic);
            ps.setString(11, joinType);
            ps.setTimestamp(12, Timestamp.valueOf(request.startAt()));
            ps.setTimestamp(13, Timestamp.valueOf(request.endAt()));
            ps.setString(14, status);
            ps.setTimestamp(15, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Contest creation failed");
        }

        long contestId = key.longValue();
        insertAudit(adminId, "CREATE_CONTEST", "CONTEST", contestId, request.title());
        publishContestEvent("CONTEST_CREATED", contestId);
        return new ContestMutationResponse(contestId, status, "Contest created");
    }

    @Transactional(readOnly = true)
    public ContestDetailResponse getContest(long contestId) {
        ensureContestExists(contestId);
        return jdbcTemplate.queryForObject("""
                        select c.id,
                               c.title,
                               c.description,
                               c.seed_money,
                               c.max_participants,
                               c.max_order_amount,
                               c.max_stock_ratio,
                               c.stock_type,
                               c.profit_criteria,
                               c.is_public,
                               c.join_type,
                               c.start_at,
                               c.end_at,
                               c.status,
                               c.admin_id,
                               coalesce(a.nickname, a.login_id, 'admin') as admin_name,
                               c.created_at,
                               c.updated_at,
                               coalesce(cp.participant_count, 0) as participant_count
                        from contest c
                        left join admin a on a.id = c.admin_id
                        left join (
                            select contest_id, count(*) as participant_count
                            from contest_participation
                            where status = 'ACTIVE'
                            group by contest_id
                        ) cp on cp.contest_id = c.id
                        where c.id = ?
                        """,
                (rs, rowNum) -> new ContestDetailResponse(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getBigDecimal("seed_money"),
                        rs.getObject("max_participants", Integer.class),
                        rs.getBigDecimal("max_order_amount"),
                        rs.getBigDecimal("max_stock_ratio"),
                        rs.getString("stock_type"),
                        rs.getString("profit_criteria"),
                        rs.getBoolean("is_public"),
                        rs.getString("join_type"),
                        toLocalDateTime(rs.getTimestamp("start_at")),
                        toLocalDateTime(rs.getTimestamp("end_at")),
                        rs.getString("status"),
                        rs.getLong("participant_count"),
                        rs.getObject("admin_id", Long.class),
                        rs.getString("admin_name"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ),
                contestId);
    }

    @Transactional
    public ContestMutationResponse updateContest(long contestId, ContestUpdateRequest request) {
        ensureContestExists(contestId);
        validateContestTimes(request.startAt(), request.endAt());
        String status = normalizeStatus(request.status(), List.of("SCHEDULED", "ACTIVE", "CLOSING_SOON", "ENDED", "CANCELED"), "contest status", "SCHEDULED");
        String profitCriteria = normalizeStatus(request.profitCriteria(), List.of("RATE", "AMOUNT"), "profit criteria", "RATE");
        String joinType = normalizeStatus(request.joinType(), List.of("ALL", "INVITE"), "join type", "ALL");
        boolean isPublic = request.isPublic() == null || request.isPublic();

        jdbcTemplate.update("""
                update contest
                set title = ?,
                    description = ?,
                    seed_money = ?,
                    max_participants = ?,
                    max_order_amount = ?,
                    max_stock_ratio = ?,
                    stock_type = ?,
                    profit_criteria = ?,
                    is_public = ?,
                    join_type = ?,
                    start_at = ?,
                    end_at = ?,
                    status = ?,
                    updated_at = ?
                where id = ?
                """,
                request.title(),
                request.description(),
                request.seedMoney(),
                request.maxParticipants(),
                request.maxOrderAmount(),
                request.maxStockRatio(),
                request.stockType(),
                profitCriteria,
                isPublic,
                joinType,
                request.startAt(),
                request.endAt(),
                status,
                LocalDateTime.now(),
                contestId);

        insertAudit(currentAdminId(), "UPDATE_CONTEST", "CONTEST", contestId, request.title());
        publishContestEvent("CONTEST_UPDATED", contestId);
        return new ContestMutationResponse(contestId, status, "Contest updated");
    }

    @Transactional
    public ContestMutationResponse endContest(long contestId) {
        updateContestStatus(contestId, "ENDED", "END_CONTEST");
        publishContestEvent("CONTEST_CLOSED", contestId);
        return new ContestMutationResponse(contestId, "ENDED", "Contest ended");
    }

    @Transactional
    public ContestMutationResponse cancelContest(long contestId) {
        updateContestStatus(contestId, "CANCELED", "CANCEL_CONTEST");
        publishContestEvent("CONTEST_DELETED", contestId);
        return new ContestMutationResponse(contestId, "CANCELED", "Contest canceled");
    }

    @Transactional(readOnly = true)
    public ContestResultResponse getContestResult(long contestId) {
        ContestBasic contest = getContestBasic(contestId);
        RankingStatsResponse stats = getRankingStats(contestId);
        List<RankingItem> topRankings = queryRankings(contestId, null, "INCLUDED", 0, 10).content();
        return new ContestResultResponse(
                contest.id(),
                contest.title(),
                contest.status(),
                stats.participantCount(),
                stats.averageProfitRate(),
                stats.highestProfitRate(),
                stats.highestTotalAsset(),
                topRankings
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<RankingItem> getRankings(long contestId, String keyword, String excluded, int page, int size) {
        validatePage(page, size);
        ensureContestExists(contestId);
        return queryRankings(contestId, keyword, excluded, page, size);
    }

    private int recalculateRankings(long contestId) {
        ContestBasic contest = getContestBasic(contestId);
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update("""
                insert into ranking (
                    contest_id,
                    member_id,
                    total_asset,
                    profit_amount,
                    profit_rate,
                    rank_no,
                    is_excluded,
                    updated_at
                )
                select cp.contest_id,
                       cp.member_id,
                       coalesce(ps.total_asset, cp.seed_money),
                       coalesce(ps.profit_amount, coalesce(ps.total_asset, cp.seed_money) - cp.seed_money),
                       coalesce(ps.profit_rate, 0),
                       null,
                       false,
                       ?
                from contest_participation cp
                left join portfolio_snapshot ps on ps.member_id = cp.member_id and ps.contest_id = cp.contest_id
                left join ranking r on r.contest_id = cp.contest_id and r.member_id = cp.member_id
                where cp.contest_id = ? and cp.status = 'ACTIVE' and r.id is null
                """, now, contestId);

        jdbcTemplate.update("""
                update ranking r
                join contest_participation cp on cp.contest_id = r.contest_id and cp.member_id = r.member_id
                left join portfolio_snapshot ps on ps.member_id = r.member_id and ps.contest_id = r.contest_id
                set r.total_asset = coalesce(ps.total_asset, cp.seed_money),
                    r.profit_amount = coalesce(ps.profit_amount, coalesce(ps.total_asset, cp.seed_money) - cp.seed_money),
                    r.profit_rate = coalesce(ps.profit_rate, 0),
                    r.updated_at = ?
                where r.contest_id = ?
                """, now, contestId);

        String orderBy = "AMOUNT".equals(contest.profitCriteria())
                ? "coalesce(profit_amount, 0) desc, coalesce(total_asset, 0) desc, member_id asc"
                : "coalesce(profit_rate, 0) desc, coalesce(total_asset, 0) desc, member_id asc";

        List<Long> rankingIds = jdbcTemplate.query("""
                        select id
                        from ranking
                        where contest_id = ? and is_excluded = false
                        order by %s
                        """.formatted(orderBy),
                (rs, rowNum) -> rs.getLong("id"),
                contestId);

        int rank = 1;
        for (Long rankingId : rankingIds) {
            jdbcTemplate.update("update ranking set rank_no = ?, updated_at = ? where id = ?", rank++, now, rankingId);
        }

        jdbcTemplate.update("""
                update ranking
                set rank_no = null, updated_at = ?
                where contest_id = ? and is_excluded = true
                """, now, contestId);

        return rankingIds.size();
    }

    @Transactional(readOnly = true)
    public String exportRankings(long contestId, String keyword, String excluded) {
        ensureContestExists(contestId);
        List<RankingItem> rows = queryRankings(contestId, keyword, excluded, 0, 10_000).content();
        StringBuilder csv = new StringBuilder("contestId,memberId,nickname,accountId,totalAsset,profitAmount,profitRate,rankNo,isExcluded,excludedReason,updatedAt\n");
        for (RankingItem row : rows) {
            csv.append(csvLine(Arrays.asList(
                    row.contestId(),
                    row.memberId(),
                    row.nickname(),
                    row.accountId(),
                    row.totalAsset(),
                    row.profitAmount(),
                    row.profitRate(),
                    row.rankNo(),
                    row.isExcluded(),
                    row.excludedReason(),
                    row.updatedAt()
            )));
        }
        return csv.toString();
    }

    @Transactional
    public RankingStatusResponse excludeRanking(long contestId, long memberId, RankingExcludeRequest request) {
        ensureRankingTarget(contestId, memberId);
        jdbcTemplate.update("""
                update ranking
                set is_excluded = true,
                    excluded_reason = ?,
                    excluded_at = ?,
                    excluded_by_admin_id = ?,
                    rank_no = null,
                    updated_at = ?
                where contest_id = ? and member_id = ?
                """, request.reason(), LocalDateTime.now(), currentAdminId(), LocalDateTime.now(), contestId, memberId);
        insertAudit(currentAdminId(), "EXCLUDE_RANKING", "MEMBER", memberId, request.reason());
        recalculateRankings(contestId);
        return new RankingStatusResponse(contestId, memberId, true, "Ranking excluded");
    }

    @Transactional
    public RankingStatusResponse restoreRanking(long contestId, long memberId) {
        ensureRankingTarget(contestId, memberId);
        jdbcTemplate.update("""
                update ranking
                set is_excluded = false,
                    excluded_reason = null,
                    excluded_at = null,
                    excluded_by_admin_id = null,
                    updated_at = ?
                where contest_id = ? and member_id = ?
                """, LocalDateTime.now(), contestId, memberId);
        insertAudit(currentAdminId(), "RESTORE_RANKING", "MEMBER", memberId, "Restore contest ranking");
        recalculateRankings(contestId);
        return new RankingStatusResponse(contestId, memberId, false, "Ranking restored");
    }

    @Transactional(readOnly = true)
    public RankingStatsResponse getRankingStats(long contestId) {
        ensureContestExists(contestId);
        return jdbcTemplate.queryForObject("""
                        select ? as contest_id,
                               (select count(*) from contest_participation where contest_id = ? and status = 'ACTIVE') as participant_count,
                               count(case when r.is_excluded = false then 1 end) as ranked_count,
                               count(case when r.is_excluded = true then 1 end) as excluded_count,
                               count(case when r.is_excluded = false and r.profit_rate > 0 then 1 end) as profit_count,
                               count(case when r.is_excluded = false and r.profit_rate < 0 then 1 end) as loss_count,
                               coalesce(avg(case when r.is_excluded = false then r.profit_rate end), 0) as average_profit_rate,
                               coalesce(max(case when r.is_excluded = false then r.profit_rate end), 0) as highest_profit_rate,
                               coalesce(min(case when r.is_excluded = false then r.profit_rate end), 0) as lowest_profit_rate,
                               coalesce(max(case when r.is_excluded = false then r.total_asset end), 0) as highest_total_asset,
                               max(r.updated_at) as last_updated_at
                        from ranking r
                        where r.contest_id = ?
                        """,
                (rs, rowNum) -> new RankingStatsResponse(
                        rs.getLong("contest_id"),
                        rs.getLong("participant_count"),
                        rs.getLong("ranked_count"),
                        rs.getLong("excluded_count"),
                        rs.getLong("profit_count"),
                        rs.getLong("loss_count"),
                        rs.getBigDecimal("average_profit_rate"),
                        rs.getBigDecimal("highest_profit_rate"),
                        rs.getBigDecimal("lowest_profit_rate"),
                        rs.getBigDecimal("highest_total_asset"),
                        toLocalDateTime(rs.getTimestamp("last_updated_at"))
                ),
                contestId, contestId, contestId);
    }

    private PageResponse<RankingItem> queryRankings(long contestId, String keyword, String excluded, int page, int size) {
        String normalizedExcluded = normalizeStatus(excluded, List.of("ALL", "INCLUDED", "EXCLUDED"), "excluded", "ALL");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        conditions.add("r.contest_id = ?");
        args.add(contestId);
        if ("INCLUDED".equals(normalizedExcluded)) {
            conditions.add("r.is_excluded = false");
        } else if ("EXCLUDED".equals(normalizedExcluded)) {
            conditions.add("r.is_excluded = true");
        }
        if (normalizedKeyword != null) {
            conditions.add("(m.nickname like ? or m.login_id like ? or m.email like ?)");
            String like = "%" + normalizedKeyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        String where = "where " + String.join(" and ", conditions);
        long total = count("""
                select count(*)
                from ranking r
                join member_snapshot m on m.member_id = r.member_id
                %s
                """.formatted(where), args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(page * size);
        List<RankingItem> content = jdbcTemplate.query("""
                        select r.contest_id,
                               r.member_id,
                               m.nickname,
                               m.login_id,
                               r.total_asset,
                               r.profit_amount,
                               r.profit_rate,
                               r.rank_no,
                               r.is_excluded,
                               r.excluded_reason,
                               r.updated_at
                        from ranking r
                        join member_snapshot m on m.member_id = r.member_id
                        %s
                        order by r.is_excluded asc, r.rank_no asc, r.profit_rate desc, r.member_id asc
                        limit ? offset ?
                        """.formatted(where),
                (rs, rowNum) -> new RankingItem(
                        rs.getLong("contest_id"),
                        rs.getLong("member_id"),
                        rs.getString("nickname"),
                        rs.getString("login_id"),
                        rs.getBigDecimal("total_asset"),
                        rs.getBigDecimal("profit_amount"),
                        rs.getBigDecimal("profit_rate"),
                        rs.getObject("rank_no", Integer.class),
                        rs.getBoolean("is_excluded"),
                        rs.getString("excluded_reason"),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ),
                queryArgs.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    private ContestFilter contestFilter(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "SCHEDULED", "ACTIVE", "CLOSING_SOON", "ENDED", "CANCELED"), "contest status", "ALL");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (!"ALL".equals(normalizedStatus)) {
            conditions.add("c.status = ?");
            args.add(normalizedStatus);
        }
        if (normalizedKeyword != null) {
            conditions.add("(c.title like ? or c.description like ?)");
            String like = "%" + normalizedKeyword + "%";
            args.add(like);
            args.add(like);
        }
        if (startDate != null) {
            conditions.add("c.start_at >= ?");
            args.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            conditions.add("c.end_at < ?");
            args.add(endDate.plusDays(1).atStartOfDay());
        }

        return new ContestFilter(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
    }

    private void updateContestStatus(long contestId, String status, String action) {
        ensureContestExists(contestId);
        jdbcTemplate.update("""
                update contest
                set status = ?, updated_at = ?
                where id = ?
                """, status, LocalDateTime.now(), contestId);
        insertAudit(currentAdminId(), action, "CONTEST", contestId, status);
    }

    private void publishContestEvent(String eventType, long contestId) {
        contestEventPublisher.publish(getContestEvent(eventType, contestId));
    }

    private ContestEvent getContestEvent(String eventType, long contestId) {
        return jdbcTemplate.queryForObject("""
                        select id,
                               title,
                               status,
                               seed_money,
                               start_at,
                               end_at,
                               created_at,
                               updated_at
                        from contest
                        where id = ?
                        """,
                (rs, rowNum) -> new ContestEvent(
                        eventType,
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getBigDecimal("seed_money"),
                        toLocalDateTime(rs.getTimestamp("start_at")),
                        toLocalDateTime(rs.getTimestamp("end_at")),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ),
                contestId);
    }

    private void ensureRankingTarget(long contestId, long memberId) {
        ensureContestExists(contestId);
        if (count("""
                select count(*)
                from contest_participation
                where contest_id = ? and member_id = ?
                """, contestId, memberId) == 0) {
            throw notFound("Contest participant not found");
        }
        recalculateRankings(contestId);
        if (count("select count(*) from ranking where contest_id = ? and member_id = ?", contestId, memberId) == 0) {
            throw notFound("Ranking not found");
        }
    }

    private void ensureContestExists(long contestId) {
        if (count("select count(*) from contest where id = ?", contestId) == 0) {
            throw notFound("Contest not found");
        }
    }

    private ContestBasic getContestBasic(long contestId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select id, title, status, profit_criteria
                            from contest
                            where id = ?
                            """,
                    (rs, rowNum) -> new ContestBasic(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("status"),
                            rs.getString("profit_criteria")
                    ),
                    contestId);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("Contest not found");
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

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw badRequest("startDate must be before or equal to endDate");
        }
    }

    private void validateContestTimes(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw badRequest("startAt must be before endAt");
        }
    }

    private String normalizeStatus(String value, List<String> allowed, String label, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
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

    private int totalPages(long total, int size) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    private String period(Timestamp startAt, Timestamp endAt) {
        if (startAt == null || endAt == null) {
            return "";
        }
        return PERIOD_FORMATTER.format(startAt.toLocalDateTime()) + "-" + PERIOD_FORMATTER.format(endAt.toLocalDateTime());
    }

    private void setInteger(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, value);
        }
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

    private record ContestFilter(String whereClause, List<Object> args) {
    }

    private record ContestBasic(long id, String title, String status, String profitCriteria) {
    }
}
