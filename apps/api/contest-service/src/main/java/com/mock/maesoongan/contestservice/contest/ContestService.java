package com.mock.maesoongan.contestservice.contest;

import com.mock.maesoongan.contestservice.common.BusinessException;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestDetailResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestJoinResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestListItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestResultResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestStockItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.MyRankingResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.OrderValidationRequest;
import com.mock.maesoongan.contestservice.contest.ContestDtos.OrderValidationResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.PageInfo;
import com.mock.maesoongan.contestservice.contest.ContestDtos.PageResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.RankingItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ContestService {

    private final JdbcTemplate jdbcTemplate;

    public ContestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContestListItem> getContests(
            long memberId,
            String keyword,
            String status,
            String participation,
            int page,
            int size
    ) {
        validatePage(page, size);
        ContestFilter filter = contestFilter(memberId, keyword, status, participation);
        long total = count("select count(*) from contest c " + filter.memberJoin() + " " + filter.whereClause(), filter.args().toArray());

        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add(page * size);

        List<ContestListItem> content = jdbcTemplate.query("""
                        select c.id,
                               c.title,
                               c.description,
                               c.seed_money,
                               c.max_participants,
                               c.status,
                               c.start_at,
                               c.end_at,
                               me.member_id as joined_member_id,
                               coalesce(participants.participant_count, 0) as participant_count
                        from contest c
                        %s
                        left join (
                            select contest_id, count(*) as participant_count
                            from contest_participation
                            where status <> 'WITHDRAWN'
                            group by contest_id
                        ) participants on participants.contest_id = c.id
                        %s
                        order by c.start_at desc, c.id desc
                        limit ? offset ?
                        """.formatted(filter.memberJoin(), filter.whereClause()),
                (rs, rowNum) -> {
                    boolean joined = rs.getObject("joined_member_id") != null;
                    int maxParticipants = rs.getObject("max_participants") == null ? 0 : rs.getInt("max_participants");
                    long participantCount = rs.getLong("participant_count");
                    JoinAvailability availability = joinAvailability(
                            rs.getString("status"),
                            toLocalDateTime(rs.getTimestamp("start_at")),
                            maxParticipants == 0 ? null : maxParticipants,
                            participantCount,
                            joined
                    );
                    return new ContestListItem(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getBigDecimal("seed_money"),
                            rs.getObject("max_participants", Integer.class),
                            participantCount,
                            rs.getString("status"),
                            joined,
                            availability.joinable(),
                            availability.reason(),
                            toLocalDateTime(rs.getTimestamp("start_at")),
                            toLocalDateTime(rs.getTimestamp("end_at"))
                    );
                },
                args.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public ContestDetailResponse getContest(long contestId, long memberId) {
        ContestRow contest = findContest(contestId);
        if (contest == null || !isVisibleContest(contest, memberId)) {
            throw notFound("Contest not found");
        }

        boolean joined = isJoined(contestId, memberId);
        long participantCount = participantCount(contestId);
        JoinAvailability availability = joinAvailability(contest.status(), contest.startAt(), contest.maxParticipants(), participantCount, joined);
        PortfolioRow portfolio = findPortfolio(contestId, memberId);
        RankingRow ranking = findRanking(contestId, memberId);

        return new ContestDetailResponse(
                contest.id(),
                contest.title(),
                contest.description(),
                contest.seedMoney(),
                contest.maxParticipants(),
                contest.maxOrderAmount(),
                contest.maxStockRatio(),
                contest.stockType(),
                contest.profitCriteria(),
                contest.isPublic(),
                contest.joinType(),
                contest.status(),
                participantCount,
                joined,
                availability.joinable(),
                availability.reason(),
                portfolio == null ? null : portfolio.totalAsset(),
                portfolio == null ? null : portfolio.profitAmount(),
                portfolio == null ? null : portfolio.profitRate(),
                ranking == null ? null : ranking.rankNo(),
                contest.startAt(),
                contest.endAt()
        );
    }

    @Transactional
    public ContestJoinResponse joinContest(long contestId, long memberId) {
        ContestRow contest = findContest(contestId);
        if (contest == null || !contest.isPublic() || !"ALL".equals(contest.joinType())) {
            throw notFound("Contest not found");
        }
        if (isJoined(contestId, memberId)) {
            throw badRequest("Already joined contest");
        }

        long participantCount = participantCount(contestId);
        JoinAvailability availability = joinAvailability(contest.status(), contest.startAt(), contest.maxParticipants(), participantCount, false);
        if (!availability.joinable()) {
            throw badRequest(availability.reason());
        }

        jdbcTemplate.update("""
                insert into contest_participation (contest_id, member_id, seed_money, status, joined_at)
                values (?, ?, ?, 'ACTIVE', ?)
                """, contestId, memberId, contest.seedMoney(), LocalDateTime.now());

        return new ContestJoinResponse(
                contestId,
                memberId,
                "ACTIVE",
                "PENDING",
                "Contest joined. Contest account provisioning is pending."
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ContestListItem> getMyContests(long memberId, String status, int page, int size) {
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "ACTIVE", "ENDED"), "contest status", "ALL");
        List<Object> args = new ArrayList<>();
        args.add(memberId);

        List<String> conditions = new ArrayList<>();
        conditions.add("me.member_id = ?");
        conditions.add("me.status <> 'WITHDRAWN'");
        if ("ACTIVE".equals(normalizedStatus)) {
            conditions.add("c.status in ('SCHEDULED', 'ACTIVE', 'CLOSING_SOON')");
        } else if ("ENDED".equals(normalizedStatus)) {
            conditions.add("c.status = 'ENDED'");
        }
        String where = "where " + String.join(" and ", conditions);

        long total = count("""
                select count(*)
                from contest c
                join contest_participation me on me.contest_id = c.id
                %s
                """.formatted(where), args.toArray());

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(page * size);
        List<ContestListItem> content = jdbcTemplate.query("""
                        select c.id,
                               c.title,
                               c.description,
                               c.seed_money,
                               c.max_participants,
                               c.status,
                               c.start_at,
                               c.end_at,
                               coalesce(participants.participant_count, 0) as participant_count
                        from contest c
                        join contest_participation me on me.contest_id = c.id
                        left join (
                            select contest_id, count(*) as participant_count
                            from contest_participation
                            where status <> 'WITHDRAWN'
                            group by contest_id
                        ) participants on participants.contest_id = c.id
                        %s
                        order by c.start_at desc, c.id desc
                        limit ? offset ?
                        """.formatted(where),
                (rs, rowNum) -> new ContestListItem(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getBigDecimal("seed_money"),
                        rs.getObject("max_participants", Integer.class),
                        rs.getLong("participant_count"),
                        rs.getString("status"),
                        true,
                        false,
                        "ALREADY_JOINED",
                        toLocalDateTime(rs.getTimestamp("start_at")),
                        toLocalDateTime(rs.getTimestamp("end_at"))
                ),
                queryArgs.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContestStockItem> getContestStocks(
            long contestId,
            String keyword,
            String market,
            int page,
            int size
    ) {
        validatePage(page, size);
        ContestRow contest = requireContest(contestId);
        String normalizedKeyword = normalize(keyword);
        String normalizedMarket = normalize(market);
        List<Object> args = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        String fromClause = "from stock s";

        if ("CUSTOM".equals(contest.stockType())) {
            fromClause += " join contest_stock cs on cs.stock_id = s.id and cs.contest_id = ?";
            args.add(contestId);
        } else if (contest.stockType() != null && !"ALL".equals(contest.stockType())) {
            conditions.add("s.category = ?");
            args.add(contest.stockType());
        }

        conditions.add("s.status = 'ACTIVE'");
        if (normalizedKeyword != null) {
            conditions.add("(s.name like ? or s.code like ?)");
            String like = "%" + normalizedKeyword + "%";
            args.add(like);
            args.add(like);
        }
        if (normalizedMarket != null) {
            conditions.add("s.market = ?");
            args.add(normalizedMarket.toUpperCase(Locale.ROOT));
        }
        String where = "where " + String.join(" and ", conditions);

        long total = count("select count(*) " + fromClause + " " + where, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(page * size);

        List<ContestStockItem> content = jdbcTemplate.query("""
                        select s.id, s.code, s.name, s.market, s.category, s.status
                        %s
                        %s
                        order by s.market asc, s.name asc, s.id asc
                        limit ? offset ?
                        """.formatted(fromClause, where),
                (rs, rowNum) -> new ContestStockItem(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("market"),
                        rs.getString("category"),
                        rs.getString("status")
                ),
                queryArgs.toArray());

        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public PageResponse<RankingItem> getRankings(long contestId, int page, int size) {
        validatePage(page, size);
        requireContest(contestId);
        long total = count("""
                select count(*)
                from ranking r
                where r.contest_id = ? and r.is_excluded = false
                """, contestId);

        List<RankingItem> content = queryRankings(contestId, page, size);
        return new PageResponse<>(content, total, totalPages(total, size), page);
    }

    @Transactional(readOnly = true)
    public MyRankingResponse getMyRanking(long contestId, long memberId) {
        requireContest(contestId);
        if (!isJoined(contestId, memberId)) {
            throw notFound("Contest participation not found");
        }

        RankingRow ranking = findRanking(contestId, memberId);
        PortfolioRow portfolio = findPortfolio(contestId, memberId);
        return new MyRankingResponse(
                contestId,
                memberId,
                true,
                ranking == null ? null : ranking.rankNo(),
                ranking == null ? portfolio == null ? null : portfolio.totalAsset() : ranking.totalAsset(),
                ranking == null ? portfolio == null ? null : portfolio.profitAmount() : ranking.profitAmount(),
                ranking == null ? portfolio == null ? null : portfolio.profitRate() : ranking.profitRate(),
                ranking != null && ranking.excluded(),
                ranking == null ? null : ranking.excludedReason()
        );
    }

    @Transactional(readOnly = true)
    public ContestResultResponse getContestResult(long contestId, long memberId, int page, int size) {
        validatePage(page, size);
        ContestRow contest = requireContest(contestId);
        if (!"ENDED".equals(contest.status())) {
            throw badRequest("Contest is not ended");
        }

        PageResponse<RankingItem> rankings = getRankings(contestId, page, size);
        MyRankingResponse myResult = isJoined(contestId, memberId) ? getMyRanking(contestId, memberId) : null;
        return new ContestResultResponse(
                contest.id(),
                contest.title(),
                contest.endAt(),
                participantCount(contestId),
                myResult,
                rankings.content(),
                new PageInfo(rankings.currentPage(), rankings.totalPages())
        );
    }

    @Transactional(readOnly = true)
    public OrderValidationResponse validateOrder(long contestId, OrderValidationRequest request) {
        ContestRow contest = findContest(contestId);
        if (contest == null) {
            return invalid(contestId, request, "CONTEST_NOT_FOUND", null, null);
        }
        if (!"ACTIVE".equals(contest.status()) && !"CLOSING_SOON".equals(contest.status())) {
            return invalid(contestId, request, "CONTEST_NOT_ACTIVE", contest.maxOrderAmount(), contest.maxStockRatio());
        }
        if (!isJoined(contestId, request.memberId())) {
            return invalid(contestId, request, "NOT_PARTICIPATED", contest.maxOrderAmount(), contest.maxStockRatio());
        }
        if (!isTradableStock(contest, request.stockId())) {
            return invalid(contestId, request, "STOCK_NOT_TRADABLE", contest.maxOrderAmount(), contest.maxStockRatio());
        }
        if (contest.maxOrderAmount() != null && request.orderAmount().compareTo(contest.maxOrderAmount()) > 0) {
            return invalid(contestId, request, "MAX_ORDER_AMOUNT_EXCEEDED", contest.maxOrderAmount(), contest.maxStockRatio());
        }
        if (contest.maxStockRatio() != null
                && request.stockRatioAfterOrder() != null
                && request.stockRatioAfterOrder().compareTo(contest.maxStockRatio()) > 0) {
            return invalid(contestId, request, "MAX_STOCK_RATIO_EXCEEDED", contest.maxOrderAmount(), contest.maxStockRatio());
        }

        return new OrderValidationResponse(true, null, contestId, request.memberId(), request.stockId(), contest.maxOrderAmount(), contest.maxStockRatio());
    }

    private List<RankingItem> queryRankings(long contestId, int page, int size) {
        return jdbcTemplate.query("""
                        select r.member_id,
                               m.nickname,
                               r.rank_no,
                               r.total_asset,
                               r.profit_amount,
                               r.profit_rate
                        from ranking r
                        join member_snapshot m on m.member_id = r.member_id
                        where r.contest_id = ? and r.is_excluded = false
                        order by r.rank_no asc, r.profit_rate desc, r.member_id asc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new RankingItem(
                        rs.getLong("member_id"),
                        rs.getString("nickname"),
                        rs.getObject("rank_no", Integer.class),
                        rs.getBigDecimal("total_asset"),
                        rs.getBigDecimal("profit_amount"),
                        rs.getBigDecimal("profit_rate")
                ),
                contestId,
                size,
                page * size);
    }

    private ContestFilter contestFilter(long memberId, String keyword, String status, String participation) {
        String normalizedStatus = normalizeStatus(status, List.of("ALL", "SCHEDULED", "ACTIVE", "CLOSING_SOON", "ENDED"), "contest status", "ALL");
        String normalizedParticipation = normalizeStatus(participation, List.of("ALL", "JOINED", "NOT_JOINED"), "participation", "ALL");
        String normalizedKeyword = normalize(keyword);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String memberJoin = "left join contest_participation me on me.contest_id = c.id and me.member_id = ? and me.status <> 'WITHDRAWN'";
        args.add(memberId);

        conditions.add("c.status <> 'CANCELED'");
        conditions.add("(c.is_public = true or me.member_id is not null)");
        if ("ACTIVE".equals(normalizedStatus)) {
            conditions.add("c.status in ('ACTIVE', 'CLOSING_SOON')");
        } else if (!"ALL".equals(normalizedStatus)) {
            conditions.add("c.status = ?");
            args.add(normalizedStatus);
        }
        if ("JOINED".equals(normalizedParticipation)) {
            conditions.add("me.member_id is not null");
        } else if ("NOT_JOINED".equals(normalizedParticipation)) {
            conditions.add("me.member_id is null");
        }
        if (normalizedKeyword != null) {
            conditions.add("(c.title like ? or c.description like ?)");
            String like = "%" + normalizedKeyword + "%";
            args.add(like);
            args.add(like);
        }

        return new ContestFilter(memberJoin, "where " + String.join(" and ", conditions), args);
    }

    private ContestRow findContest(long contestId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select id,
                                   title,
                                   description,
                                   seed_money,
                                   max_participants,
                                   max_order_amount,
                                   max_stock_ratio,
                                   coalesce(stock_type, 'ALL') as stock_type,
                                   profit_criteria,
                                   is_public,
                                   join_type,
                                   status,
                                   start_at,
                                   end_at
                            from contest
                            where id = ?
                            """, this::mapContest, contestId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private ContestRow requireContest(long contestId) {
        ContestRow contest = findContest(contestId);
        if (contest == null) {
            throw notFound("Contest not found");
        }
        return contest;
    }

    private ContestRow mapContest(ResultSet rs, int rowNum) throws SQLException {
        return new ContestRow(
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
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("start_at")),
                toLocalDateTime(rs.getTimestamp("end_at"))
        );
    }

    private boolean isVisibleContest(ContestRow contest, long memberId) {
        return !"CANCELED".equals(contest.status()) && (contest.isPublic() || isJoined(contest.id(), memberId));
    }

    private boolean isJoined(long contestId, Long memberId) {
        return count("""
                select count(*)
                from contest_participation
                where contest_id = ? and member_id = ? and status <> 'WITHDRAWN'
                """, contestId, memberId) > 0;
    }

    private long participantCount(long contestId) {
        return count("""
                select count(*)
                from contest_participation
                where contest_id = ? and status <> 'WITHDRAWN'
                """, contestId);
    }

    private PortfolioRow findPortfolio(long contestId, long memberId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select total_asset, profit_amount, profit_rate
                            from portfolio_snapshot
                            where contest_id = ? and member_id = ?
                            """,
                    (rs, rowNum) -> new PortfolioRow(
                            rs.getBigDecimal("total_asset"),
                            rs.getBigDecimal("profit_amount"),
                            rs.getBigDecimal("profit_rate")
                    ),
                    contestId,
                    memberId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private RankingRow findRanking(long contestId, long memberId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select rank_no, total_asset, profit_amount, profit_rate, is_excluded, excluded_reason
                            from ranking
                            where contest_id = ? and member_id = ?
                            """,
                    (rs, rowNum) -> new RankingRow(
                            rs.getObject("rank_no", Integer.class),
                            rs.getBigDecimal("total_asset"),
                            rs.getBigDecimal("profit_amount"),
                            rs.getBigDecimal("profit_rate"),
                            rs.getBoolean("is_excluded"),
                            rs.getString("excluded_reason")
                    ),
                    contestId,
                    memberId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private boolean isTradableStock(ContestRow contest, Long stockId) {
        if (stockId == null || count("select count(*) from stock where id = ? and status = 'ACTIVE'", stockId) == 0) {
            return false;
        }
        if ("CUSTOM".equals(contest.stockType())) {
            return count("select count(*) from contest_stock where contest_id = ? and stock_id = ?", contest.id(), stockId) > 0;
        }
        if (contest.stockType() == null || "ALL".equals(contest.stockType())) {
            return true;
        }
        return count("select count(*) from stock where id = ? and category = ? and status = 'ACTIVE'", stockId, contest.stockType()) > 0;
    }

    private JoinAvailability joinAvailability(
            String status,
            LocalDateTime startAt,
            Integer maxParticipants,
            long participantCount,
            boolean joined
    ) {
        if (joined) {
            return new JoinAvailability(false, "ALREADY_JOINED");
        }
        if (!"SCHEDULED".equals(status)) {
            return new JoinAvailability(false, "CONTEST_NOT_JOINABLE_STATUS");
        }
        if (!LocalDateTime.now().isBefore(startAt)) {
            return new JoinAvailability(false, "CONTEST_ALREADY_STARTED");
        }
        if (maxParticipants != null && participantCount >= maxParticipants) {
            return new JoinAvailability(false, "MAX_PARTICIPANTS_EXCEEDED");
        }
        return new JoinAvailability(true, null);
    }

    private OrderValidationResponse invalid(
            long contestId,
            OrderValidationRequest request,
            String reason,
            BigDecimal maxOrderAmount,
            BigDecimal maxStockRatio
    ) {
        return new OrderValidationResponse(
                false,
                reason,
                contestId,
                request == null ? null : request.memberId(),
                request == null ? null : request.stockId(),
                maxOrderAmount,
                maxStockRatio
        );
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
        if (!allowed.contains(normalized)) {
            throw badRequest("Invalid " + label + ": " + value);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private record ContestFilter(String memberJoin, String whereClause, List<Object> args) {
    }

    private record ContestRow(
            long id,
            String title,
            String description,
            BigDecimal seedMoney,
            Integer maxParticipants,
            BigDecimal maxOrderAmount,
            BigDecimal maxStockRatio,
            String stockType,
            String profitCriteria,
            boolean isPublic,
            String joinType,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    private record PortfolioRow(BigDecimal totalAsset, BigDecimal profitAmount, BigDecimal profitRate) {
    }

    private record RankingRow(
            Integer rankNo,
            BigDecimal totalAsset,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            boolean excluded,
            String excludedReason
    ) {
    }

    private record JoinAvailability(boolean joinable, String reason) {
    }
}
