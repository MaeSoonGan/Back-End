package com.mock.maesoongan.contestservice.contest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대회 상태(status)를 시작/종료 시각에 맞춰 자동 전환한다.
 * 생성 시 SCHEDULED로 저장되고 별도 전환이 없으면 시작일이 지나도 "개최 예정"으로 남기 때문에,
 * 주기적으로 start_at/end_at 기준으로 status를 SCHEDULED → ACTIVE → ENDED로 갱신한다.
 * (CANCELED는 건드리지 않음)
 */
@Component
public class ContestStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContestStatusScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public ContestStatusScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${contest.status.poll-interval-ms:60000}")
    public void syncContestStatus() {
        try {
            // 시작 시각이 지났고 아직 안 끝난 대회: SCHEDULED → ACTIVE
            int activated = jdbcTemplate.update("""
                    update contest
                    set status = 'ACTIVE'
                    where status = 'SCHEDULED'
                      and start_at <= now()
                      and (end_at is null or end_at > now())
                    """);

            // 종료 시각이 지난 대회: SCHEDULED/ACTIVE/CLOSING_SOON → ENDED
            int ended = jdbcTemplate.update("""
                    update contest
                    set status = 'ENDED'
                    where status in ('SCHEDULED', 'ACTIVE', 'CLOSING_SOON')
                      and end_at is not null
                      and end_at <= now()
                    """);

            if (activated > 0 || ended > 0) {
                log.info("contest status synced: activated={}, ended={}", activated, ended);
            }
        } catch (RuntimeException exception) {
            log.warn("contest status sync failed: {}", exception.getMessage());
        }
    }
}
