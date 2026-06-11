package com.mock.maesoongan.contestservice.contest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 대회 시작/종료 알림 스케줄러.
 * 시작/종료 시각이 지난 대회 중 아직 알림을 안 보낸 건을 찾아 참여자에게 알림을 생성한다.
 * 중복 방지는 notification 테이블에 해당 대회+종류 알림이 이미 있는지로 판단(스키마 변경 없음).
 * 최초 실행 시 과거 대회 대량 백필 방지를 위해 최근 1일 내 이벤트만 대상으로 한다.
 */
@Component
public class ContestNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContestNotificationScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public ContestNotificationScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${contest.notify.poll-interval-ms:60000}")
    public void run() {
        try {
            notifyStarted();
        } catch (RuntimeException exception) {
            log.warn("Contest start notification failed: {}", exception.getMessage());
        }
        try {
            notifyEnded();
        } catch (RuntimeException exception) {
            log.warn("Contest end notification failed: {}", exception.getMessage());
        }
    }

    private void notifyStarted() {
        List<Map<String, Object>> contests = jdbcTemplate.queryForList("""
                select c.id, c.title
                from contest c
                where c.start_at <= now()
                  and c.start_at >= now() - interval 1 day
                  and (c.end_at is null or c.end_at > now())
                  and not exists (
                      select 1 from notification n
                      where n.type = 'CONTEST_START' and n.target_type = 'CONTEST' and n.target_id = c.id
                  )
                """);
        for (Map<String, Object> contest : contests) {
            long contestId = ((Number) contest.get("id")).longValue();
            String title = (String) contest.get("title");
            jdbcTemplate.update("""
                    insert into notification
                        (member_id, type, title, body, is_read, target_type, target_id, delivery_status, retry_count, created_at)
                    select p.member_id, 'CONTEST_START', '대회 시작', ?, 0, 'CONTEST', ?, 'CREATED', 0, now()
                    from contest_participation p
                    join notification_setting s on s.member_id = p.member_id and s.contest_start = 1
                    where p.contest_id = ? and p.status = 'ACTIVE'
                    """, (title == null ? "참여 중인 대회" : title) + " 대회가 시작됐어요", contestId, contestId);
        }
    }

    private void notifyEnded() {
        List<Map<String, Object>> contests = jdbcTemplate.queryForList("""
                select c.id, c.title
                from contest c
                where c.end_at is not null
                  and c.end_at <= now()
                  and c.end_at >= now() - interval 1 day
                  and not exists (
                      select 1 from notification n
                      where n.type = 'CONTEST_END' and n.target_type = 'CONTEST' and n.target_id = c.id
                  )
                """);
        for (Map<String, Object> contest : contests) {
            long contestId = ((Number) contest.get("id")).longValue();
            String title = (String) contest.get("title");
            jdbcTemplate.update("""
                    insert into notification
                        (member_id, type, title, body, is_read, target_type, target_id, delivery_status, retry_count, created_at)
                    select p.member_id, 'CONTEST_END', '대회 종료', ?, 0, 'CONTEST', ?, 'CREATED', 0, now()
                    from contest_participation p
                    join notification_setting s on s.member_id = p.member_id and s.contest_end = 1
                    where p.contest_id = ? and p.status = 'ACTIVE'
                    """, (title == null ? "참여한 대회" : title) + " 대회가 종료됐어요", contestId, contestId);
        }
    }
}
