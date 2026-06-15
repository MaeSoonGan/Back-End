package com.mock.maesoongan.adminservice.contest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 진행중(ACTIVE) 대회의 참가자 순위를 주기적으로 재계산한다.
 * 기존엔 admin이 참가자 제외/복원할 때만 순위가 갱신돼, 일반 대회는 순위가 매겨지지 않았다.
 * - 대회 1건당 독립 트랜잭션(AdminContestService.recalculateContestRankings)으로 처리해
 *   한 대회 실패가 전체를 막지 않도록 한다.
 */
@Component
public class ContestRankingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContestRankingScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final AdminContestService adminContestService;

    public ContestRankingScheduler(JdbcTemplate jdbcTemplate, AdminContestService adminContestService) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminContestService = adminContestService;
    }

    @Scheduled(fixedDelayString = "${admin.ranking.recalc-interval-ms:60000}")
    public void recalculateActiveContestRankings() {
        List<Long> contestIds = jdbcTemplate.queryForList(
                "select id from contest where status = 'ACTIVE'", Long.class);
        if (contestIds.isEmpty()) {
            return;
        }

        int updated = 0;
        for (Long contestId : contestIds) {
            try {
                adminContestService.recalculateContestRankings(contestId);
                updated++;
            } catch (RuntimeException exception) {
                log.warn("contest ranking recalc failed: contestId={}, err={}", contestId, exception.getMessage());
            }
        }
        if (updated > 0) {
            log.debug("contest ranking recalc done: {} active contest(s)", updated);
        }
    }
}
