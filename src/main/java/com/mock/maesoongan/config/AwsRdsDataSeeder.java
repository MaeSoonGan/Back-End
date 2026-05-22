package com.mock.maesoongan.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("rds-seed")
public class AwsRdsDataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ConfigurableApplicationContext applicationContext;

    public AwsRdsDataSeeder(JdbcTemplate jdbcTemplate, ConfigurableApplicationContext applicationContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationContext = applicationContext;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmins();
        seedMembers();
        seedMemberAuth();
        seedStocks();
        seedContests();
        seedContestStocks();
        seedContestParticipations();
        seedRankings();
        seedPortfolios();
        seedOrders();
        seedTrades();
        seedWatchlists();
        seedNotifications();
        seedMonitoringStatus();
        seedAccountSuspensions();
        seedSeedHistories();
        seedAuditLogs();
        seedNotices();
        seedSyncEventLogs();
        new Thread(applicationContext::close).start();
    }

    private void seedAdmins() {
        for (int id = 1; id <= 5; id++) {
            jdbcTemplate.update("""
                            insert into admin
                            (id, login_id, password, email, nickname, role, status, created_at, deleted_at, updated_at)
                            values (?, ?, '{noop}admin1234!', ?, ?, ?, 'ACTIVE', ?, null, null)
                            on duplicate key update
                                email = values(email),
                                nickname = values(nickname),
                                role = values(role),
                                status = values(status)
                            """,
                    id,
                    "admin" + String.format("%02d", id),
                    "admin" + id + "@maesoongan.com",
                    id == 1 ? "superadmin" : "admin" + String.format("%02d", id),
                    id == 1 ? "SUPER_ADMIN" : "ADMIN",
                    LocalDateTime.now().minusDays(120L - id)
            );
        }
    }

    private void seedMembers() {
        String[] names = {"홍길동", "김철수", "이영희", "박민수", "최지우", "정하늘", "오세훈", "강민지", "윤서준", "임수아"};
        for (long id = 1; id <= 300; id++) {
            String status = id % 31 == 0 ? "DELETED" : id % 9 == 0 ? "SUSPENDED" : "ACTIVE";
            LocalDateTime createdAt = id <= 18
                    ? LocalDateTime.now().minusHours(id % 12)
                    : LocalDateTime.now().minusDays(id % 180);

            jdbcTemplate.update("""
                            insert into member_snapshot
                            (member_id, login_id, email, nickname, phone, status, login_fail_count, email_verified,
                             profile_image_url, created_at, updated_at, synced_at)
                            values (?, ?, ?, ?, ?, ?, ?, true, ?, ?, null, ?)
                            on duplicate key update
                                email = values(email),
                                nickname = values(nickname),
                                phone = values(phone),
                                status = values(status),
                                login_fail_count = values(login_fail_count),
                                synced_at = values(synced_at)
                            """,
                    id,
                    "user" + String.format("%04d", id),
                    "user" + id + "@example.com",
                    names[(int) (id % names.length)] + id,
                    "010-" + String.format("%04d", 1000 + id % 9000) + "-" + String.format("%04d", 9000 - id % 9000),
                    status,
                    (int) (id % 7),
                    "https://example.com/profiles/" + id + ".png",
                    createdAt,
                    LocalDateTime.now()
            );
        }
    }

    private void seedMemberAuth() {
        for (long id = 1; id <= 300; id++) {
            jdbcTemplate.update("""
                            insert into dev_member_auth
                            (member_id, password_hash, password_updated_at, login_fail_count, locked_until, created_at, updated_at)
                            values (?, '{noop}password123!', null, ?, null, ?, null)
                            on duplicate key update
                                login_fail_count = values(login_fail_count),
                                updated_at = values(updated_at)
                            """,
                    id,
                    (int) (id % 7),
                    LocalDateTime.now().minusDays(id % 180)
            );
        }
    }

    private void seedStocks() {
        List<Object[]> stocks = List.of(
                new Object[]{"005930", "삼성전자", "KOSPI", "반도체"},
                new Object[]{"000660", "SK하이닉스", "KOSPI", "반도체"},
                new Object[]{"035420", "NAVER", "KOSPI", "IT"},
                new Object[]{"035720", "카카오", "KOSPI", "IT"},
                new Object[]{"005380", "현대차", "KOSPI", "자동차"},
                new Object[]{"000270", "기아", "KOSPI", "자동차"},
                new Object[]{"068270", "셀트리온", "KOSPI", "바이오"},
                new Object[]{"207940", "삼성바이오로직스", "KOSPI", "바이오"},
                new Object[]{"373220", "LG에너지솔루션", "KOSPI", "2차전지"},
                new Object[]{"051910", "LG화학", "KOSPI", "화학"},
                new Object[]{"006400", "삼성SDI", "KOSPI", "2차전지"},
                new Object[]{"247540", "에코프로비엠", "KOSDAQ", "2차전지"},
                new Object[]{"086520", "에코프로", "KOSDAQ", "2차전지"},
                new Object[]{"091990", "셀트리온헬스케어", "KOSDAQ", "바이오"},
                new Object[]{"112040", "위메이드", "KOSDAQ", "게임"},
                new Object[]{"251270", "넷마블", "KOSPI", "게임"},
                new Object[]{"066570", "LG전자", "KOSPI", "전자"},
                new Object[]{"012330", "현대모비스", "KOSPI", "자동차"},
                new Object[]{"028260", "삼성물산", "KOSPI", "건설"},
                new Object[]{"096770", "SK이노베이션", "KOSPI", "에너지"}
        );

        long id = 1;
        for (Object[] stock : stocks) {
            jdbcTemplate.update("""
                            insert into stock
                            (id, code, name, market, category, status, created_at, updated_at)
                            values (?, ?, ?, ?, ?, 'ACTIVE', ?, null)
                            on duplicate key update
                                name = values(name),
                                market = values(market),
                                category = values(category),
                                status = values(status)
                            """,
                    id++, stock[0], stock[1], stock[2], stock[3], LocalDateTime.now().minusDays(60));
        }
    }

    private void seedContests() {
        Object[][] contests = {
                {1L, "5월 정기 대회", "전체 종목 모의투자 대회", "ACTIVE", 1, 31},
                {2L, "반도체 특별전", "반도체 종목 중심 대회", "ACTIVE", 5, 20},
                {3L, "IT 성장주 챌린지", "IT 업종 수익률 경쟁", "SCHEDULED", 10, 40},
                {4L, "4월 정기 대회", "종료된 정기 대회", "ENDED", -45, -15},
                {5L, "바이오 집중 투자전", "바이오 종목 대회", "CANCELED", -20, 10}
        };

        for (Object[] contest : contests) {
            long id = (long) contest[0];
            int startOffset = (int) contest[4];
            int endOffset = (int) contest[5];
            jdbcTemplate.update("""
                            insert into contest
                            (id, admin_id, title, description, seed_money, max_participants, max_order_amount,
                             max_stock_ratio, stock_type, profit_criteria, is_public, join_type, start_at, end_at,
                             status, created_at, updated_at)
                            values (?, 1, ?, ?, 10000000, ?, 5000000, 30.00, 'ALL', 'RATE', true, 'ALL', ?, ?, ?, ?, null)
                            on duplicate key update
                                title = values(title),
                                description = values(description),
                                status = values(status),
                                start_at = values(start_at),
                                end_at = values(end_at)
                            """,
                    id,
                    contest[1],
                    contest[2],
                    id == 2 ? 100 : 1000,
                    LocalDateTime.now().minusDays(startOffset),
                    LocalDateTime.now().plusDays(endOffset),
                    contest[3],
                    LocalDateTime.now().minusDays(30)
            );
        }
    }

    private void seedContestStocks() {
        long id = 1;
        for (long contestId = 1; contestId <= 5; contestId++) {
            for (long stockId = 1; stockId <= 20; stockId++) {
                if (contestId == 2 && stockId > 2) {
                    continue;
                }
                jdbcTemplate.update("""
                                insert into contest_stock (id, contest_id, stock_id)
                                values (?, ?, ?)
                                on duplicate key update stock_id = values(stock_id)
                                """,
                        id++, contestId, stockId);
            }
        }
    }

    private void seedContestParticipations() {
        long id = 1;
        for (long memberId = 1; memberId <= 260; memberId++) {
            int contestCount = (int) (memberId % 4) + 1;
            for (long contestId = 1; contestId <= contestCount; contestId++) {
                jdbcTemplate.update("""
                                insert into contest_participation
                                (id, contest_id, member_id, seed_money, status, joined_at, updated_at)
                                values (?, ?, ?, 10000000, ?, ?, null)
                                on duplicate key update
                                    status = values(status),
                                    updated_at = values(updated_at)
                                """,
                        id++,
                        contestId,
                        memberId,
                        contestId == 4 ? "ENDED" : "ACTIVE",
                        LocalDateTime.now().minusDays((memberId + contestId) % 30)
                );
            }
        }
    }

    private void seedRankings() {
        long id = 1;
        for (long contestId = 1; contestId <= 4; contestId++) {
            for (long memberId = 1; memberId <= 180; memberId++) {
                BigDecimal totalAsset = BigDecimal.valueOf(8_000_000L + ((memberId * 37_000L + contestId * 210_000L) % 8_000_000L));
                BigDecimal profitAmount = totalAsset.subtract(new BigDecimal("10000000"));
                BigDecimal profitRate = profitAmount.divide(new BigDecimal("10000000"), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                jdbcTemplate.update("""
                                insert into ranking
                                (id, contest_id, member_id, total_asset, profit_amount, profit_rate, rank_no,
                                 is_excluded, excluded_reason, excluded_at, excluded_by_admin_id, updated_at)
                                values (?, ?, ?, ?, ?, ?, ?, false, null, null, null, ?)
                                on duplicate key update
                                    total_asset = values(total_asset),
                                    profit_amount = values(profit_amount),
                                    profit_rate = values(profit_rate),
                                    rank_no = values(rank_no),
                                    updated_at = values(updated_at)
                                """,
                        id++, contestId, memberId, totalAsset, profitAmount, profitRate,
                        (int) ((memberId + contestId) % 180 + 1), LocalDateTime.now());
            }
        }
    }

    private void seedPortfolios() {
        long id = 1;
        for (long memberId = 1; memberId <= 300; memberId++) {
            for (long contestId = 0; contestId <= 3; contestId++) {
                BigDecimal cash = BigDecimal.valueOf(2_000_000L + ((memberId + contestId) * 150_000L) % 5_000_000L);
                BigDecimal stockEvaluation = BigDecimal.valueOf(3_000_000L + ((memberId + contestId) * 210_000L) % 7_000_000L);
                BigDecimal totalAsset = cash.add(stockEvaluation);
                BigDecimal profitAmount = totalAsset.subtract(new BigDecimal("10000000"));
                BigDecimal profitRate = profitAmount.divide(new BigDecimal("10000000"), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                jdbcTemplate.update("""
                                insert into portfolio_snapshot
                                (id, member_id, contest_id, cash_balance, available_cash, stock_evaluation_amount,
                                 total_asset, total_buy_amount, total_sell_amount, profit_amount, profit_rate,
                                 holdings_json, portfolio_version, onprem_updated_at, synced_at)
                                values (?, ?, ?, ?, ?, ?, ?, 7000000, 3000000, ?, ?, ?, ?, ?, ?)
                                on duplicate key update
                                    cash_balance = values(cash_balance),
                                    available_cash = values(available_cash),
                                    stock_evaluation_amount = values(stock_evaluation_amount),
                                    total_asset = values(total_asset),
                                    profit_amount = values(profit_amount),
                                    profit_rate = values(profit_rate),
                                    holdings_json = values(holdings_json),
                                    portfolio_version = values(portfolio_version),
                                    synced_at = values(synced_at)
                                """,
                        id++, memberId, contestId, cash, cash.multiply(new BigDecimal("0.8")), stockEvaluation,
                        totalAsset, profitAmount, profitRate,
                        "[{\"stockCode\":\"005930\",\"quantity\":10},{\"stockCode\":\"035420\",\"quantity\":3}]",
                        memberId + contestId,
                        LocalDateTime.now().minusMinutes(memberId % 60),
                        LocalDateTime.now()
                );
            }
        }
    }

    private void seedOrders() {
        for (long id = 1; id <= 500; id++) {
            long memberId = (id % 250) + 1;
            long stockId = (id % 20) + 1;
            String side = id % 2 == 0 ? "BUY" : "SELL";
            String status = id % 17 == 0 ? "REJECTED" : id % 11 == 0 ? "CANCELED" : id % 5 == 0 ? "FILLED" : "OPEN";
            jdbcTemplate.update("""
                            insert into order_snapshot
                            (order_id, member_id, contest_id, stock_id, stock_code, stock_name, side, order_type,
                             order_price, order_quantity, remaining_quantity, status, reject_reason, ordered_at,
                             updated_at, synced_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            on duplicate key update
                                status = values(status),
                                remaining_quantity = values(remaining_quantity),
                                updated_at = values(updated_at),
                                synced_at = values(synced_at)
                            """,
                    5000 + id,
                    memberId,
                    id % 4,
                    stockId,
                    stockCode(stockId),
                    "종목" + stockId,
                    side,
                    id % 3 == 0 ? "MARKET" : "LIMIT",
                    BigDecimal.valueOf(50_000L + (id % 120) * 1_000L),
                    (int) (id % 20 + 1),
                    status.equals("FILLED") || status.equals("CANCELED") ? 0 : (int) (id % 10),
                    status,
                    status.equals("REJECTED") ? "주문 가능 금액 부족" : null,
                    LocalDateTime.now().minusMinutes(id * 3),
                    LocalDateTime.now().minusMinutes(id),
                    LocalDateTime.now()
            );
        }
    }

    private void seedTrades() {
        for (long id = 1; id <= 260; id++) {
            long orderId = 5000 + id;
            long memberId = (id % 250) + 1;
            long stockId = (id % 20) + 1;
            BigDecimal price = BigDecimal.valueOf(50_000L + (id % 120) * 1_000L);
            int quantity = (int) (id % 20 + 1);
            jdbcTemplate.update("""
                            insert into trade_history
                            (trade_id, order_id, member_id, contest_id, stock_id, stock_code, stock_name, side,
                             executed_price, executed_quantity, executed_amount, executed_at, synced_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            on duplicate key update
                                executed_price = values(executed_price),
                                executed_quantity = values(executed_quantity),
                                executed_amount = values(executed_amount),
                                synced_at = values(synced_at)
                            """,
                    9000 + id, orderId, memberId, id % 4, stockId, stockCode(stockId), "종목" + stockId,
                    id % 2 == 0 ? "BUY" : "SELL", price, quantity,
                    price.multiply(BigDecimal.valueOf(quantity)),
                    LocalDateTime.now().minusMinutes(id * 2),
                    LocalDateTime.now()
            );
        }
    }

    private void seedWatchlists() {
        long id = 1;
        for (long memberId = 1; memberId <= 180; memberId++) {
            for (long i = 1; i <= 3; i++) {
                long stockId = ((memberId + i) % 20) + 1;
                jdbcTemplate.update("""
                                insert into watchlist (id, member_id, stock_id, created_at)
                                values (?, ?, ?, ?)
                                on duplicate key update created_at = values(created_at)
                                """,
                        id++, memberId, stockId, LocalDateTime.now().minusDays(i));
            }
        }
    }

    private void seedNotifications() {
        for (long id = 1; id <= 400; id++) {
            long memberId = (id % 250) + 1;
            String type = id % 4 == 0 ? "TRADE_EXECUTED" : id % 4 == 1 ? "ORDER_ACCEPTED" : id % 4 == 2 ? "CONTEST_ENDED" : "NOTICE";
            jdbcTemplate.update("""
                            insert into notification
                            (id, member_id, type, title, body, is_read, read_at, target_type, target_id,
                             delivery_status, retry_count, last_retry_at, delivery_failure_reason, created_at)
                            values (?, ?, ?, ?, ?, ?, null, ?, ?, 'SENT', 0, null, null, ?)
                            on duplicate key update
                                title = values(title),
                                body = values(body),
                                is_read = values(is_read),
                                delivery_status = values(delivery_status)
                            """,
                    id,
                    memberId,
                    type,
                    "알림 " + id,
                    "테스트 알림 메시지입니다.",
                    id % 3 == 0,
                    type.startsWith("ORDER") || type.startsWith("TRADE") ? "ORDER" : "CONTEST",
                    type.startsWith("ORDER") || type.startsWith("TRADE") ? 5000 + id : id % 5 + 1,
                    LocalDateTime.now().minusMinutes(id)
            );
        }
    }

    private void seedMonitoringStatus() {
        for (long id = 1; id <= 30; id++) {
            String status = id % 5 == 0 ? "IGNORED" : id % 7 == 0 ? "RESOLVED" : "PENDING";
            jdbcTemplate.update("""
                            insert into monitoring_status
                            (id, status_type, target_type, target_id, service_name, severity, status, title, message,
                             is_maintenance, ignored_by_admin_id, ignored_at, resolved_at, checked_at, created_at, updated_at)
                            values (?, 'ABNORMAL_DETECTION', ?, ?, 'order-service', ?, ?, ?, ?, false, null, null, null, ?, ?, null)
                            on duplicate key update
                                severity = values(severity),
                                status = values(status),
                                title = values(title),
                                message = values(message),
                                checked_at = values(checked_at)
                            """,
                    id,
                    id % 2 == 0 ? "ORDER" : "MEMBER",
                    id % 2 == 0 ? 5000 + id : id,
                    id % 3 == 0 ? "CRITICAL" : "WARN",
                    status,
                    "비정상 탐지 " + id,
                    id % 2 == 0 ? "짧은 시간 내 반복 주문이 감지되었습니다." : "로그인 실패 후 주문 시도가 감지되었습니다.",
                    LocalDateTime.now().minusMinutes(id * 5),
                    LocalDateTime.now().minusHours(id)
            );
        }
    }

    private void seedAccountSuspensions() {
        for (long id = 1; id <= 25; id++) {
            jdbcTemplate.update("""
                            insert into account_suspension
                            (id, member_id, admin_id, reason, status, suspended_until, released_at, release_admin_id,
                             created_at, updated_at)
                            values (?, ?, 1, ?, 'SUSPENDED', null, null, null, ?, null)
                            on duplicate key update
                                reason = values(reason),
                                status = values(status)
                            """,
                    id, id * 9, "비정상 거래 패턴 탐지", LocalDateTime.now().minusDays(id));
        }
    }

    private void seedSeedHistories() {
        for (long id = 1; id <= 80; id++) {
            jdbcTemplate.update("""
                            insert into seed_history
                            (id, member_id, admin_id, contest_id, amount, reason, request_status, failure_reason,
                             created_at, processed_at)
                            values (?, ?, 1, ?, ?, ?, 'SUCCESS', null, ?, ?)
                            on duplicate key update
                                amount = values(amount),
                                request_status = values(request_status),
                                processed_at = values(processed_at)
                            """,
                    id,
                    (id % 250) + 1,
                    id % 4,
                    BigDecimal.valueOf(1_000_000L + (id % 5) * 1_000_000L),
                    "관리자 테스트 시드머니 지급",
                    LocalDateTime.now().minusDays(id % 30),
                    LocalDateTime.now().minusDays(id % 30).plusMinutes(10)
            );
        }
    }

    private void seedAuditLogs() {
        for (long id = 1; id <= 150; id++) {
            String action = id % 4 == 0 ? "SUSPEND_MEMBER" : id % 4 == 1 ? "SEED_PAYMENT" : id % 4 == 2 ? "FORCE_CANCEL" : "CREATE_CONTEST";
            String targetType = action.equals("CREATE_CONTEST") ? "CONTEST" : action.equals("FORCE_CANCEL") ? "ORDER" : "MEMBER";
            jdbcTemplate.update("""
                            insert into audit_log
                            (id, admin_id, action, target_type, target_id, reason, result, ip_address, user_agent, created_at)
                            values (?, ?, ?, ?, ?, ?, 'SUCCESS', '127.0.0.1', 'seed-script', ?)
                            on duplicate key update
                                action = values(action),
                                target_type = values(target_type),
                                target_id = values(target_id),
                                reason = values(reason),
                                result = values(result)
                            """,
                    id, (id % 5) + 1, action, targetType, id, "개발용 더미 감사 로그", LocalDateTime.now().minusMinutes(id * 15));
        }
    }

    private void seedNotices() {
        for (long id = 1; id <= 20; id++) {
            jdbcTemplate.update("""
                            insert into notice
                            (id, admin_id, title, content, is_pinned, status, start_at, end_at, created_at, updated_at)
                            values (?, 1, ?, ?, ?, 'PUBLISHED', null, null, ?, null)
                            on duplicate key update
                                title = values(title),
                                content = values(content),
                                is_pinned = values(is_pinned),
                                status = values(status)
                            """,
                    id,
                    "공지사항 " + id,
                    "개발용 공지사항 본문입니다.",
                    id <= 3,
                    LocalDateTime.now().minusDays(id)
            );
        }
    }

    private void seedSyncEventLogs() {
        for (long id = 1; id <= 120; id++) {
            jdbcTemplate.update("""
                            insert into sync_event_log
                            (id, event_id, event_type, aggregate_type, aggregate_id, process_status, failure_reason,
                             received_at, processed_at)
                            values (?, ?, ?, ?, ?, ?, null, ?, ?)
                            on duplicate key update
                                process_status = values(process_status),
                                processed_at = values(processed_at)
                            """,
                    id,
                    "event-" + id,
                    id % 3 == 0 ? "execution-completed" : id % 3 == 1 ? "order-accepted" : "account-updated",
                    id % 3 == 0 ? "TRADE" : id % 3 == 1 ? "ORDER" : "ACCOUNT",
                    String.valueOf(id),
                    id % 13 == 0 ? "FAILED" : "SUCCESS",
                    LocalDateTime.now().minusMinutes(id * 2),
                    LocalDateTime.now().minusMinutes(id * 2 - 1)
            );
        }
    }

    private String stockCode(long stockId) {
        return switch ((int) stockId) {
            case 1 -> "005930";
            case 2 -> "000660";
            case 3 -> "035420";
            case 4 -> "035720";
            case 5 -> "005380";
            case 6 -> "000270";
            case 7 -> "068270";
            case 8 -> "207940";
            case 9 -> "373220";
            case 10 -> "051910";
            default -> "STK" + String.format("%03d", stockId);
        };
    }
}
