package com.mock.maesoongan.notificationapi.notification.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 장 시작(평일 09:00) / 장 마감(평일 15:30) 알림 스케줄러.
 * 해당 수신설정(market_open/market_close)을 켠 활성 회원에게 알림을 한 번에 생성한다(INSERT...SELECT).
 */
@Component
public class MarketNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketNotificationScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public MarketNotificationScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    public void notifyMarketOpen() {
        fanOut("MARKET_OPEN", "장 시작", "오늘 주식시장이 개장했습니다", "market_open");
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Seoul")
    public void notifyMarketClose() {
        fanOut("MARKET_CLOSE", "장 마감", "오늘 장이 마감됐습니다", "market_close");
    }

    private void fanOut(String type, String title, String body, String settingColumn) {
        try {
            int created = jdbcTemplate.update("""
                    insert into notification
                        (member_id, type, title, body, is_read, target_type, target_id, delivery_status, retry_count, created_at)
                    select s.member_id, ?, ?, ?, 0, null, null, 'CREATED', 0, now()
                    from notification_setting s
                    join member_snapshot m on m.member_id = s.member_id and m.status = 'ACTIVE'
                    where s.""" + settingColumn + " = 1", type, title, body);
            log.info("Market notification fan-out done. type={}, created={}", type, created);
        } catch (RuntimeException exception) {
            log.warn("Market notification fan-out failed. type={}, err={}", type, exception.getMessage());
        }
    }
}
