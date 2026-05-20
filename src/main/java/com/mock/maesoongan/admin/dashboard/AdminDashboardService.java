package com.mock.maesoongan.admin.dashboard;

import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ActivityListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ActivitySummary;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ActiveContestSummary;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.AlertListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.AlertSummary;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.CancelOrderResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestDetail;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.ContestStatisticsResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DailyOrder;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DailyOrderListResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.DashboardResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.IgnoreAlertResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.SuspendUserResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.TodayOrderStatisticsResponse;
import com.mock.maesoongan.admin.dashboard.AdminDashboardDtos.UserStatisticsResponse;
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
import com.mock.maesoongan.common.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final List<String> ACTIVE_CONTEST_STATUSES = List.of("ACTIVE", "CLOSING_SOON");
    private static final List<String> ALERT_STATUSES = List.of("PENDING", "IGNORED", "RESOLVED");
    private static final List<String> CONTEST_STATUSES = List.of("ACTIVE", "CLOSING_SOON", "ENDED");

    private final DashboardMemberRepository memberRepository;
    private final DashboardOrderRepository orderRepository;
    private final DashboardOrderStatRepository orderStatRepository;
    private final DashboardContestRepository contestRepository;
    private final DashboardAlertRepository alertRepository;
    private final DashboardActivityRepository activityRepository;

    public AdminDashboardService(DashboardMemberRepository memberRepository,
                                 DashboardOrderRepository orderRepository,
                                 DashboardOrderStatRepository orderStatRepository,
                                 DashboardContestRepository contestRepository,
                                 DashboardAlertRepository alertRepository,
                                 DashboardActivityRepository activityRepository) {
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.orderStatRepository = orderStatRepository;
        this.contestRepository = contestRepository;
        this.alertRepository = alertRepository;
        this.activityRepository = activityRepository;
    }

    public DashboardResponse getDashboard() {
        UserStatisticsResponse users = getUserStatistics();
        TodayOrderStatisticsResponse orders = getTodayOrderStatistics();
        ContestStatisticsResponse contests = getContestStatistics();
        AlertListResponse alerts = getAlerts("PENDING", 10);

        return new DashboardResponse(
                users.totalUsers(),
                users.todayNewUsers(),
                orders.totalOrderCount(),
                orders.completedOrderCount(),
                contests.activeContestCount(),
                contests.totalParticipantCount(),
                alerts.alertCount(),
                alerts.alerts(),
                getDailyOrders(7).orders(),
                getContestSummaries(),
                getActivities(5).activities(),
                LocalDateTime.now()
        );
    }

    public UserStatisticsResponse getUserStatistics() {
        LocalDate today = LocalDate.now();
        long todayNewUsers = memberRepository.countByJoinedAtGreaterThanEqualAndJoinedAtLessThan(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        return new UserStatisticsResponse(memberRepository.count(), todayNewUsers);
    }

    public TodayOrderStatisticsResponse getTodayOrderStatistics() {
        DashboardOrderStat todayStat = orderStatRepository.findById(LocalDate.now())
                .orElse(new DashboardOrderStat(LocalDate.now(), 0, 0));

        return new TodayOrderStatisticsResponse(
                todayStat.getTotalOrderCount(),
                todayStat.getCompletedOrderCount()
        );
    }

    public ContestStatisticsResponse getContestStatistics() {
        List<DashboardContest> contests = contestRepository.findByStatusInOrderByStartDateAsc(ACTIVE_CONTEST_STATUSES);
        long participantCount = contests.stream()
                .mapToLong(DashboardContest::getParticipantCount)
                .sum();

        return new ContestStatisticsResponse(contestRepository.countByStatusIn(ACTIVE_CONTEST_STATUSES), participantCount);
    }

    public AlertListResponse getAlerts(String status, int limit) {
        validateLimit(limit);
        if (status != null && !ALERT_STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ALERT_STATUS", "잘못된 알림 상태입니다.");
        }

        List<DashboardAlert> alerts = status == null
                ? alertRepository.findAllByOrderByDetectedAtDesc(PageRequest.of(0, limit))
                : alertRepository.findByStatusOrderByDetectedAtDesc(status, PageRequest.of(0, limit));

        long alertCount = status == null ? alertRepository.count() : alertRepository.countByStatus(status);
        List<AlertSummary> summaries = alerts.stream()
                .map(this::toAlertSummary)
                .toList();

        String systemStatus = alertRepository.countByStatus("PENDING") > 0 ? "WARNING" : "NORMAL";
        return new AlertListResponse(alertCount, systemStatus, summaries);
    }

    @Transactional
    public SuspendUserResponse suspendUser(long userId, String reason) {
        DashboardMember member = memberRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다."));
        if (member.isSuspended()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ALREADY_SUSPENDED", "이미 정지된 회원입니다.");
        }

        LocalDateTime suspendedAt = LocalDateTime.now();
        member.suspend(suspendedAt, reason);
        activityRepository.save(newActivity("MEMBER_SUSPEND", withReason("회원 " + userId + " 계정 정지", reason)));

        return new SuspendUserResponse(userId, "SUSPENDED", suspendedAt, "회원 계정이 정지되었습니다.");
    }

    @Transactional
    public CancelOrderResponse cancelOrder(long orderId, String reason) {
        DashboardOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (!order.isCancelable()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORDER_NOT_CANCELABLE", "이미 체결 완료되어 취소 불가 또는 이미 취소된 주문입니다.");
        }

        LocalDateTime canceledAt = LocalDateTime.now();
        order.cancel(canceledAt, reason);
        activityRepository.save(newActivity("ORDER_CANCEL", withReason("주문 " + orderId + " 강제 취소", reason)));

        return new CancelOrderResponse(orderId, "CANCELED", canceledAt, "주문이 강제 취소되었습니다.");
    }

    @Transactional
    public IgnoreAlertResponse ignoreAlert(long alertId, String reason) {
        DashboardAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "알림을 찾을 수 없습니다."));
        if (alert.isProcessed()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ALREADY_PROCESSED_ALERT", "이미 처리된 알림입니다.");
        }

        LocalDateTime ignoredAt = LocalDateTime.now();
        alert.ignore(ignoredAt, reason);
        activityRepository.save(newActivity("ALERT_IGNORE", withReason("알림 " + alertId + " 무시 처리", reason)));

        return new IgnoreAlertResponse(alertId, "IGNORED", ignoredAt, "알림이 무시 처리되었습니다.");
    }

    public DailyOrderListResponse getDailyOrders(int days) {
        if (days < 1 || days > 31) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAYS", "조회 일수는 1일부터 31일 사이로 입력해주세요.");
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        List<DailyOrder> orders = orderStatRepository.findByOrderDateBetweenOrderByOrderDateAsc(startDate, today)
                .stream()
                .map(stat -> new DailyOrder(stat.getOrderDate(), stat.getTotalOrderCount(), stat.getOrderDate().equals(today)))
                .toList();

        return new DailyOrderListResponse(days, orders);
    }

    public ContestListResponse getContests(String status) {
        if (status != null && !CONTEST_STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CONTEST_STATUS", "잘못된 대회 상태값입니다.");
        }

        List<DashboardContest> contests = status == null
                ? contestRepository.findByStatusInOrderByStartDateAsc(ACTIVE_CONTEST_STATUSES)
                : contestRepository.findByStatusOrderByStartDateAsc(status);

        return new ContestListResponse(contests.stream()
                .map(this::toContestDetail)
                .toList());
    }

    public ActivityListResponse getActivities(int limit) {
        validateLimit(limit);

        return new ActivityListResponse(activityRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(activity -> new ActivitySummary(
                        activity.getId(),
                        activity.getType(),
                        activity.getContent(),
                        activity.getAdminId(),
                        activity.getAdminName(),
                        activity.getCreatedAt()
                ))
                .toList());
    }

    private AlertSummary toAlertSummary(DashboardAlert alert) {
        return new AlertSummary(
                alert.getId(),
                alert.getType(),
                alert.getUserId(),
                alert.getUserName(),
                alert.getOrderId(),
                alert.getContent(),
                alert.getDetectedAt()
        );
    }

    private ContestDetail toContestDetail(DashboardContest contest) {
        return new ContestDetail(
                contest.getId(),
                contest.getName(),
                contest.getStartDate(),
                contest.getEndDate(),
                formatPeriod(contest.getStartDate(), contest.getEndDate()),
                contest.getParticipantCount(),
                contest.getMaxParticipantCount(),
                contest.getStatus(),
                contest.getStatusName()
        );
    }

    private List<ActiveContestSummary> getContestSummaries() {
        return getContests(null).contests().stream()
                .map(contest -> new ActiveContestSummary(
                        contest.contestId(),
                        contest.contestName(),
                        contest.period(),
                        contest.participantCount(),
                        contest.status()
                ))
                .toList();
    }

    private DashboardActivity newActivity(String type, String content) {
        return new DashboardActivity(type, content, 1, "admin01", LocalDateTime.now());
    }

    private String withReason(String content, String reason) {
        if (reason == null || reason.isBlank()) {
            return content;
        }
        return content + " - 사유: " + reason;
    }

    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM.dd");
        return formatter.format(startDate) + "-" + formatter.format(endDate);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "조회 개수는 1개부터 100개 사이로 입력해주세요.");
        }
    }
}
