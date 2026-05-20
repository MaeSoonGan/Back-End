package com.mock.maesoongan.admin.dashboard;

import com.mock.maesoongan.admin.dashboard.entity.DashboardActivity;
import com.mock.maesoongan.admin.dashboard.entity.DashboardAlert;
import com.mock.maesoongan.admin.dashboard.entity.DashboardContest;
import com.mock.maesoongan.admin.dashboard.entity.DashboardMember;
import com.mock.maesoongan.admin.dashboard.entity.DashboardOrder;
import com.mock.maesoongan.admin.dashboard.entity.DashboardOrderStat;
import com.mock.maesoongan.admin.dashboard.repository.DashboardActivityRepository;
import com.mock.maesoongan.admin.dashboard.repository.DashboardAlertRepository;
import com.mock.maesoongan.admin.dashboard.repository.DashboardContestRepository;
import com.mock.maesoongan.admin.dashboard.repository.DashboardMemberRepository;
import com.mock.maesoongan.admin.dashboard.repository.DashboardOrderRepository;
import com.mock.maesoongan.admin.dashboard.repository.DashboardOrderStatRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class AdminDashboardDataSeeder implements ApplicationRunner {

    private final DashboardMemberRepository memberRepository;
    private final DashboardOrderRepository orderRepository;
    private final DashboardOrderStatRepository orderStatRepository;
    private final DashboardContestRepository contestRepository;
    private final DashboardAlertRepository alertRepository;
    private final DashboardActivityRepository activityRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardDataSeeder(DashboardMemberRepository memberRepository,
                                    DashboardOrderRepository orderRepository,
                                    DashboardOrderStatRepository orderStatRepository,
                                    DashboardContestRepository contestRepository,
                                    DashboardAlertRepository alertRepository,
                                    DashboardActivityRepository activityRepository,
                                    JdbcTemplate jdbcTemplate) {
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.orderStatRepository = orderStatRepository;
        this.contestRepository = contestRepository;
        this.alertRepository = alertRepository;
        this.activityRepository = activityRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("alter table dashboard_activities modify id bigint not null auto_increment");
        activityRepository.deleteAllInBatch();
        alertRepository.deleteAllInBatch();
        contestRepository.deleteAllInBatch();
        orderStatRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        seedMembers();
        seedOrderStats();
        seedOrders();
        seedContests();
        seedAlerts();
        seedActivities();
    }

    private void seedMembers() {
        LocalDateTime now = LocalDateTime.now();
        List<DashboardMember> members = new ArrayList<>();

        members.add(new DashboardMember(101L, "이민주", "ACTIVE", now.minusDays(10)));
        members.add(new DashboardMember(102L, "박민수", "ACTIVE", now.minusDays(9)));
        members.add(new DashboardMember(1010L, "정지회원", "SUSPENDED", now.minusDays(20)));
        members.add(new DashboardMember(2000L, "추가회원", "ACTIVE", now.minusDays(40)));

        for (long id = 1; id <= 1234; id++) {
            if (id == 101 || id == 102 || id == 999 || id == 1010) {
                continue;
            }
            LocalDateTime joinedAt = id <= 12 ? now.minusHours(id % 12) : now.minusDays(30 + (id % 60));
            members.add(new DashboardMember(id, "회원" + id, "ACTIVE", joinedAt));
        }

        memberRepository.saveAll(members);
    }

    private void seedOrderStats() {
        LocalDate today = LocalDate.now();
        long[] counts = {3540, 3820, 3214, 4320, 4012, 4721, 4821};
        long[] completedCounts = {2300, 2510, 2100, 2930, 2780, 3100, 3214};

        List<DashboardOrderStat> stats = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            LocalDate date = today.minusDays(counts.length - 1L - i);
            stats.add(new DashboardOrderStat(date, counts[i], completedCounts[i]));
        }

        orderStatRepository.saveAll(stats);
    }

    private void seedOrders() {
        LocalDateTime now = LocalDateTime.now();
        orderRepository.saveAll(List.of(
                new DashboardOrder(5000L, "COMPLETED", now.minusHours(3)),
                new DashboardOrder(5001L, "PENDING", now.minusHours(2)),
                new DashboardOrder(5002L, "PENDING", now.minusHours(1))
        ));
    }

    private void seedContests() {
        contestRepository.saveAll(List.of(
                new DashboardContest(1L, "5월 정기 대회", LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31),
                        234, 1000L, "ACTIVE", "진행중"),
                new DashboardContest(2L, "반도체 특별전", LocalDate.of(2025, 5, 5), LocalDate.of(2025, 5, 20),
                        89, 100L, "CLOSING_SOON", "마감임박"),
                new DashboardContest(3L, "4월 정기 대회", LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                        410, 1000L, "ENDED", "종료")
        ));
    }

    private void seedAlerts() {
        alertRepository.saveAll(List.of(
                new DashboardAlert(1L, "ABNORMAL_ORDER", 101, "이민주", 5001L, "3분 내 50건 주문",
                        "PENDING", LocalDateTime.of(2025, 5, 8, 14, 28)),
                new DashboardAlert(2L, "ABNORMAL_ORDER", 102, "박민수", 5002L, "동일 종목 동시 반복 주문",
                        "IGNORED", LocalDateTime.of(2025, 5, 8, 13, 55)),
                new DashboardAlert(3L, "ABNORMAL_ORDER", 103, "김하늘", null, "비정상 로그인 이후 주문 시도",
                        "PENDING", LocalDateTime.of(2025, 5, 8, 12, 15))
        ));
    }

    private void seedActivities() {
        activityRepository.saveAll(List.of(
                new DashboardActivity("MEMBER_SUSPEND", "이영희 계정 정지", 1, "admin01",
                        LocalDateTime.of(2025, 5, 8, 14, 32)),
                new DashboardActivity("SEED_MONEY_PAYMENT", "홍길동 시드 100만원 지급", 1, "admin01",
                        LocalDateTime.of(2025, 5, 8, 11, 15)),
                new DashboardActivity("CONTEST_CREATE", "5월 정기 대회 생성", 2, "admin01",
                        LocalDateTime.of(2025, 5, 5, 9, 0)),
                new DashboardActivity("ORDER_CANCEL", "박민준 주문 강제 취소", 3, "admin02",
                        LocalDateTime.of(2025, 5, 6, 10, 20))
        ));
    }
}
