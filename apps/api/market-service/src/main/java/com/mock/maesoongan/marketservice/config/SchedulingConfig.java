package com.mock.maesoongan.marketservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @Scheduled 작업용 스레드 풀.
 * 기본(단일 스레드)이면 KIS REST를 도는 무거운 스케줄러(관심종목 가격 피더, 조회상위 백필)가
 * 스레드를 오래 잡아 지수/순위 스냅샷(10초)이 멈출 수 있어 멀티스레드로 분리한다.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("market-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }
}
