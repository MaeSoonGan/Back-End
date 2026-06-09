package com.mock.maesoongan.orderservice.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매 영업일 장 마감 후 전체 포트폴리오의 수익률/자산을 일별 스냅샷 테이블에 적재한다.
 * 수익률 추이(getProfitHistory) 조회의 데이터 소스.
 */
@Component
public class PortfolioDailySnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(PortfolioDailySnapshotScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PortfolioRepository portfolioRepository;

    public PortfolioDailySnapshotScheduler(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    // 평일 15:40 KST (정규장 15:30 마감 직후)
    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    public void captureDailySnapshot() {
        LocalDate today = LocalDate.now(SEOUL);
        int rows = portfolioRepository.snapshotDaily(today);
        log.info("portfolio daily snapshot captured: date={}, rows={}", today, rows);
    }
}
