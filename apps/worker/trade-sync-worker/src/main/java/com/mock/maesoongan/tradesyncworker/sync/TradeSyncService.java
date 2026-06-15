package com.mock.maesoongan.tradesyncworker.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.tradesyncworker.notification.NotificationClient;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.AccountEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.ExecutionConfirmedEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.OrderSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.OrderCancelResultEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.PortfolioSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.MemberCommandPayload;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.MemberCommandResultEvent;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.TradeSyncRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TradeSyncService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public TradeSyncService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            NotificationClient notificationClient,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.notificationClient = notificationClient;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public SyncResult syncOrder(OrderSyncRequest request) {
        SyncResult result = processEvent(
                request.eventId(),
                "ORDER_SNAPSHOT_SYNC",
                "ORDER",
                String.valueOf(request.orderId()),
                () -> upsertOrderSnapshot(request)
        );
        // 취소 확정된 주문만 알림(신규 처리 건). 트랜잭션 밖, best-effort.
        if ("SUCCESS".equals(result.processStatus())) {
            notifyOrderCanceled(request);
        }
        return result;
    }

    private void notifyOrderCanceled(OrderSyncRequest request) {
        String status = request.status() == null ? "" : request.status().toUpperCase(Locale.ROOT);
        if (!"CANCELED".equals(status) && !"CANCELLED".equals(status)) {
            return;
        }
        boolean buy = "BUY".equalsIgnoreCase(request.side());
        String stockName = (request.stockName() == null || request.stockName().isBlank())
                ? request.stockCode()
                : request.stockName();
        String reason = (request.rejectReason() == null || request.rejectReason().isBlank())
                ? "주문이 취소되었습니다"
                : request.rejectReason();
        String body = String.format(
                Locale.KOREA,
                "%s %d주 %s 주문 취소 · %s",
                stockName,
                request.orderQuantity(),
                buy ? "매수" : "매도",
                reason
        );
        notificationClient.create(request.memberId(), "ORDER_CANCELED", "주문 취소", body, "ORDER", request.orderId());
    }

    public SyncResult syncTrade(TradeSyncRequest request) {
        SyncResult result = processEvent(
                request.eventId(),
                "TRADE_HISTORY_SYNC",
                "TRADE",
                String.valueOf(request.tradeId()),
                () -> {
                    upsertTradeHistory(request);
                    updateOrderByTrade(request);
                }
        );
        // 신규 체결 성공 건만 알림(중복 처리/SKIPPED 제외). 트랜잭션 밖, best-effort.
        if ("SUCCESS".equals(result.processStatus())) {
            notifyTradeFilled(request);
        }
        return result;
    }

    public SyncResult syncExecutionConfirmed(ExecutionConfirmedEvent event) {
        OrderReference order = findOrderReference(event.orderId())
                .orElseGet(() -> fallbackOrderReference(event));
        TradeSyncRequest request = toTradeSyncRequest(event, order);
        SyncResult result = processEvent(
                request.eventId(),
                "TRADE_HISTORY_SYNC",
                "TRADE",
                String.valueOf(request.tradeId()),
                () -> {
                    upsertTradeHistory(request);
                    updateOrderByTrade(request);
                    upsertPortfolioSnapshot(event, request);
                }
        );
        if ("SUCCESS".equals(result.processStatus())) {
            notifyTradeFilled(request);
        }
        return result;
    }

    public SyncResult syncOrderCancelResult(OrderCancelResultEvent event) {
        String eventType = normalize(event.eventType());
        if (!"ORDER_CANCEL_CONFIRMED".equals(eventType) && !"ORDER_CANCEL_REJECTED".equals(eventType)) {
            throw new IllegalArgumentException("Unsupported order cancel result eventType: " + event.eventType());
        }

        return processEvent(
                event.eventId(),
                event.eventType(),
                "ORDER",
                String.valueOf(event.orderId()),
                () -> {
                    OrderReference order = findOrderReference(event.orderId())
                            .orElseThrow(() -> new IllegalArgumentException("order_snapshot not found for orderId=" + event.orderId()));
                    updateOrderByCancelResult(event, eventType);
                    updatePortfolioCashByCancelResult(event, order);
                    evictReservationCache(order.memberId(), defaultContestId(order.contestId()), event.orderId());
                }
        );
    }

    private TradeSyncRequest toTradeSyncRequest(ExecutionConfirmedEvent event) {
        OrderReference order = findOrderReference(event.orderId())
                .orElseGet(() -> fallbackOrderReference(event));

        return toTradeSyncRequest(event, order);
    }

    private TradeSyncRequest toTradeSyncRequest(ExecutionConfirmedEvent event, OrderReference order) {
        return new TradeSyncRequest(
                "execution.confirmed:" + event.executionId(),
                event.executionId(),
                event.orderId(),
                order.memberId(),
                order.contestId(),
                order.stockId(),
                nonBlank(order.stockCode(), event.stockCode()),
                nonBlank(order.stockName(), event.stockName()),
                nonBlank(order.side(), event.orderType()),
                event.executedPrice(),
                event.executedQuantity(),
                event.executedAmount(),
                event.confirmedAt()
        );
    }

    private void upsertPortfolioSnapshot(ExecutionConfirmedEvent event, TradeSyncRequest request) {
        PortfolioSnapshotState current = findPortfolioSnapshot(request.memberId(), defaultContestId(request.contestId()))
                .orElse(PortfolioSnapshotState.empty());
        List<HoldingPosition> holdings = mergeHolding(current.holdingsJson(), event);
        String holdingsJson = writeHoldingsJson(holdings);
        BigDecimal cashBalance = nonNull(event.updatedDeposit(), current.cashBalance());
        BigDecimal availableCash = nonNull(event.updatedAvailableBalance(), current.availableCash());
        BigDecimal stockEvaluationAmount = calculateStockEvaluationAmount(holdings, current.stockEvaluationAmount());
        BigDecimal totalAsset = cashBalance.add(stockEvaluationAmount);
        Long portfolioVersion = current.portfolioVersion() == null ? 1L : current.portfolioVersion() + 1L;

        jdbcTemplate.update("""
                insert into portfolio_snapshot (
                    member_id,
                    contest_id,
                    account_id,
                    cash_balance,
                    available_cash,
                    stock_evaluation_amount,
                    total_asset,
                    total_buy_amount,
                    total_sell_amount,
                    profit_amount,
                    profit_rate,
                    holdings_json,
                    portfolio_version,
                    onprem_updated_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    account_id = coalesce(values(account_id), account_id),
                    cash_balance = values(cash_balance),
                    available_cash = values(available_cash),
                    stock_evaluation_amount = values(stock_evaluation_amount),
                    total_asset = values(total_asset),
                    total_buy_amount = values(total_buy_amount),
                    total_sell_amount = values(total_sell_amount),
                    profit_amount = values(profit_amount),
                    profit_rate = values(profit_rate),
                    holdings_json = values(holdings_json),
                    portfolio_version = values(portfolio_version),
                    onprem_updated_at = values(onprem_updated_at),
                    synced_at = current_timestamp
                """,
                request.memberId(),
                defaultContestId(request.contestId()),
                event.accountId(),
                cashBalance,
                availableCash,
                stockEvaluationAmount,
                totalAsset,
                nonNull(current.totalBuyAmount(), stockEvaluationAmount),
                value(current.totalSellAmount()),
                value(current.profitAmount()),
                value(current.profitRate()),
                holdingsJson,
                portfolioVersion,
                event.confirmedAt()
        );
    }

    private Optional<PortfolioSnapshotState> findPortfolioSnapshot(long memberId, long contestId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select cash_balance,
                           available_cash,
                           stock_evaluation_amount,
                           total_buy_amount,
                           total_sell_amount,
                           profit_amount,
                           profit_rate,
                           holdings_json,
                           portfolio_version
                    from portfolio_snapshot
                    where member_id = ? and contest_id = ?
                    """, (rs, rowNum) -> new PortfolioSnapshotState(
                    rs.getBigDecimal("cash_balance"),
                    rs.getBigDecimal("available_cash"),
                    rs.getBigDecimal("stock_evaluation_amount"),
                    rs.getBigDecimal("total_buy_amount"),
                    rs.getBigDecimal("total_sell_amount"),
                    rs.getBigDecimal("profit_amount"),
                    rs.getBigDecimal("profit_rate"),
                    rs.getString("holdings_json"),
                    rs.getObject("portfolio_version", Long.class)
            ), memberId, contestId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private List<HoldingPosition> mergeHolding(String holdingsJson, ExecutionConfirmedEvent event) {
        String stockCode = normalize(event.stockCode());
        List<HoldingPosition> holdings = new ArrayList<>(parseHoldings(holdingsJson));
        holdings.removeIf(holding -> stockCode.equals(holding.stockCode()));
        int quantity = event.holdingQuantity() == null ? 0 : event.holdingQuantity();
        if (quantity > 0) {
            holdings.add(new HoldingPosition(stockCode, quantity, event.holdingAveragePrice()));
        }
        return holdings;
    }

    private List<HoldingPosition> parseHoldings(String holdingsJson) {
        if (holdingsJson == null || holdingsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(holdingsJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<HoldingPosition> holdings = new ArrayList<>();
            for (JsonNode item : root) {
                String stockCode = item.path("stockCode").asText("").trim().toUpperCase(Locale.ROOT);
                int quantity = item.path("quantity").asInt(0);
                BigDecimal avgPrice = decimal(item, "avgPrice")
                        .orElseGet(() -> decimal(item, "averagePrice")
                                .orElseGet(() -> decimal(item, "holdingAveragePrice").orElse(null)));
                if (!stockCode.isBlank() && quantity > 0) {
                    holdings.add(new HoldingPosition(stockCode, quantity, avgPrice));
                }
            }
            return holdings;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Optional<BigDecimal> decimal(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(value.decimalValue());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String writeHoldingsJson(List<HoldingPosition> holdings) {
        try {
            return objectMapper.writeValueAsString(holdings);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to serialize holdings_json", exception);
        }
    }

    private BigDecimal calculateStockEvaluationAmount(List<HoldingPosition> holdings, BigDecimal fallback) {
        BigDecimal amount = holdings.stream()
                .filter(holding -> holding.avgPrice() != null)
                .map(holding -> holding.avgPrice().multiply(BigDecimal.valueOf(holding.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return amount.compareTo(BigDecimal.ZERO) == 0 ? value(fallback) : amount;
    }

    private Optional<OrderReference> findOrderReference(Long orderId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select member_id,
                           contest_id,
                           stock_id,
                           stock_code,
                           stock_name,
                           side
                    from order_snapshot
                    where order_id = ?
                    """, (rs, rowNum) -> new OrderReference(
                    rs.getLong("member_id"),
                    rs.getLong("contest_id"),
                    rs.getLong("stock_id"),
                    rs.getString("stock_code"),
                    rs.getString("stock_name"),
                    rs.getString("side")
            ), orderId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private OrderReference fallbackOrderReference(ExecutionConfirmedEvent event) {
        StockReference stock = findStockReference(event.stockCode());
        return new OrderReference(
                event.accountId(),
                0L,
                stock.stockId(),
                stock.stockCode(),
                nonBlank(event.stockName(), stock.stockName()),
                event.orderType()
        );
    }

    private StockReference findStockReference(String stockCode) {
        return jdbcTemplate.queryForObject("""
                select id, code, name
                from stock
                where code = ?
                """, (rs, rowNum) -> new StockReference(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name")
        ), stockCode);
    }

    private void notifyTradeFilled(TradeSyncRequest request) {
        boolean buy = "BUY".equalsIgnoreCase(request.side());
        String type = buy ? "TRADE_FILLED_BUY" : "TRADE_FILLED_SELL";
        String stockName = (request.stockName() == null || request.stockName().isBlank())
                ? request.stockCode()
                : request.stockName();
        String body = String.format(
                Locale.KOREA,
                "%s %d주 %s 체결 · 체결가 %,d원",
                stockName,
                request.executedQuantity(),
                buy ? "매수" : "매도",
                request.executedPrice().longValue()
        );
        notificationClient.create(request.memberId(), type, "체결 완료", body, "ORDER", request.orderId());
    }

    public SyncResult syncPortfolio(PortfolioSyncRequest request) {
        return processEvent(
                request.eventId(),
                "PORTFOLIO_SNAPSHOT_SYNC",
                "PORTFOLIO",
                request.memberId() + ":" + defaultContestId(request.contestId()),
                () -> upsertPortfolioSnapshot(request)
        );
    }

    public SyncResult syncAccountEvent(AccountEvent event) {
        String eventId = event.effectiveEventId();
        String aggregateId = event.accountId() == null ? event.memberId() + ":" + defaultContestId(event.contestId()) : String.valueOf(event.accountId());
        if (!"SUCCESS".equalsIgnoreCase(event.status())) {
            upsertSyncEventLog(
                    eventId,
                    event.eventType(),
                    "ACCOUNT",
                    aggregateId,
                    "FAILED",
                    "Account event status is " + event.status(),
                    LocalDateTime.now()
            );
            return new SyncResult(eventId, event.eventType(), "ACCOUNT", aggregateId, "FAILED", event.status(), LocalDateTime.now());
        }

        String eventType = normalize(event.eventType());
        if (!"BASIC_ACCOUNT_CREATED".equals(eventType) && !"CONTEST_ACCOUNT_CREATED".equals(eventType)) {
            throw new IllegalArgumentException("Unsupported account eventType: " + event.eventType());
        }

        return processEvent(
                eventId,
                event.eventType(),
                "ACCOUNT",
                aggregateId,
                () -> upsertPortfolioSnapshot(toPortfolioSyncRequest(event), event.accountId())
        );
    }

    private PortfolioSyncRequest toPortfolioSyncRequest(AccountEvent event) {
        BigDecimal availableCash = event.availableCash();
        BigDecimal initialCash = event.initialCash();
        LocalDateTime createdAt = event.createdAt() == null ? LocalDateTime.now() : event.createdAt();
        return new PortfolioSyncRequest(
                event.effectiveEventId(),
                event.memberId(),
                event.contestId(),
                availableCash,
                availableCash,
                BigDecimal.ZERO,
                initialCash,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "[]",
                1L,
                createdAt
        );
    }

    public SyncResult syncMemberCommandResult(MemberCommandResultEvent event) {
        String eventId = event.effectiveEventId();
        String aggregateId = aggregateId(event);
        if (!"SUCCESS".equalsIgnoreCase(event.status())) {
            upsertSyncEventLog(
                    eventId,
                    event.eventType(),
                    "MEMBER",
                    aggregateId,
                    "FAILED",
                    truncate(event.reason()),
                    LocalDateTime.now()
            );
            return new SyncResult(eventId, event.eventType(), "MEMBER", aggregateId, "FAILED", event.reason(), LocalDateTime.now());
        }

        Runnable action = switch (normalize(event.eventType())) {
            case "SIGNUP_RESULT" -> () -> upsertMemberSnapshot(event);
            case "MEMBER_UPDATE_RESULT" -> () -> updateMemberSnapshot(event);
            case "MEMBER_DELETE_RESULT" -> () -> markMemberDeleted(event);
            case "LOGIN_VERIFY_RESULT", "LOGIN_ID_FIND_RESULT", "PASSWORD_CHANGE_RESULT", "PASSWORD_RESET_RESULT" -> () -> {
            };
            default -> throw new IllegalArgumentException("Unsupported member command result eventType: " + event.eventType());
        };

        return processEvent(
                eventId,
                event.eventType(),
                "MEMBER",
                aggregateId,
                action
        );
    }

    private String aggregateId(MemberCommandResultEvent event) {
        if (event.memberId() != null) {
            return String.valueOf(event.memberId());
        }
        if (event.payload() != null && event.payload().loginId() != null && !event.payload().loginId().isBlank()) {
            return event.payload().loginId();
        }
        return event.requestId();
    }

    private void requireMemberId(MemberCommandResultEvent event) {
        if (event.memberId() == null) {
            throw new IllegalArgumentException("memberId is required for " + event.eventType());
        }
    }

    private SyncResult processEvent(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            Runnable action
    ) {
        LocalDateTime now = LocalDateTime.now();
        if ("SUCCESS".equals(findProcessStatus(eventId))) {
            return new SyncResult(eventId, eventType, aggregateType, aggregateId, "SKIPPED", "Already processed event", now);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                action.run();
                upsertSyncEventLog(eventId, eventType, aggregateType, aggregateId, "SUCCESS", null, LocalDateTime.now());
            });
            return new SyncResult(eventId, eventType, aggregateType, aggregateId, "SUCCESS", "Sync completed", LocalDateTime.now());
        } catch (RuntimeException exception) {
            upsertSyncEventLog(eventId, eventType, aggregateType, aggregateId, "FAILED", truncate(exception.getMessage()), LocalDateTime.now());
            throw exception;
        }
    }

    private void upsertOrderSnapshot(OrderSyncRequest request) {
        jdbcTemplate.update("""
                insert into order_snapshot (
                    order_id,
                    member_id,
                    contest_id,
                    stock_id,
                    stock_code,
                    stock_name,
                    side,
                    order_type,
                    order_price,
                    order_quantity,
                    remaining_quantity,
                    status,
                    reject_reason,
                    ordered_at,
                    updated_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    member_id = values(member_id),
                    contest_id = values(contest_id),
                    stock_id = values(stock_id),
                    stock_code = values(stock_code),
                    stock_name = values(stock_name),
                    side = values(side),
                    order_type = values(order_type),
                    order_price = values(order_price),
                    order_quantity = values(order_quantity),
                    remaining_quantity = values(remaining_quantity),
                    status = values(status),
                    reject_reason = values(reject_reason),
                    ordered_at = values(ordered_at),
                    updated_at = values(updated_at),
                    synced_at = current_timestamp
                """,
                request.orderId(),
                request.memberId(),
                defaultContestId(request.contestId()),
                request.stockId(),
                request.stockCode(),
                request.stockName(),
                normalize(request.side()),
                normalize(request.orderType()),
                request.orderPrice(),
                request.orderQuantity(),
                request.remainingQuantity(),
                normalize(request.status()),
                request.rejectReason(),
                request.orderedAt(),
                request.updatedAt() == null ? LocalDateTime.now() : request.updatedAt()
        );
    }

    private void upsertTradeHistory(TradeSyncRequest request) {
        jdbcTemplate.update("""
                insert into trade_history (
                    trade_id,
                    order_id,
                    member_id,
                    contest_id,
                    stock_id,
                    stock_code,
                    stock_name,
                    side,
                    executed_price,
                    executed_quantity,
                    executed_amount,
                    executed_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    order_id = values(order_id),
                    member_id = values(member_id),
                    contest_id = values(contest_id),
                    stock_id = values(stock_id),
                    stock_code = values(stock_code),
                    stock_name = values(stock_name),
                    side = values(side),
                    executed_price = values(executed_price),
                    executed_quantity = values(executed_quantity),
                    executed_amount = values(executed_amount),
                    executed_at = values(executed_at),
                    synced_at = current_timestamp
                """,
                request.tradeId(),
                request.orderId(),
                request.memberId(),
                defaultContestId(request.contestId()),
                request.stockId(),
                request.stockCode(),
                request.stockName(),
                normalize(request.side()),
                request.executedPrice(),
                request.executedQuantity(),
                request.executedAmount(),
                request.executedAt()
        );
    }

    private void updateOrderByTrade(TradeSyncRequest request) {
        jdbcTemplate.update("""
                update order_snapshot
                set remaining_quantity = greatest(remaining_quantity - ?, 0),
                    status = case
                        when greatest(remaining_quantity - ?, 0) = 0 then 'FILLED'
                        else 'PARTIALLY_FILLED'
                    end,
                    updated_at = ?,
                    synced_at = current_timestamp
                where order_id = ?
                  and status not in ('CANCELED', 'REJECTED')
                """,
                request.executedQuantity(),
                request.executedQuantity(),
                request.executedAt(),
                request.orderId()
        );
    }

    private void updateOrderByCancelResult(OrderCancelResultEvent event, String eventType) {
        jdbcTemplate.update("""
                update order_snapshot
                set remaining_quantity = coalesce(?, remaining_quantity),
                    status = ?,
                    reject_reason = ?,
                    updated_at = ?,
                    synced_at = current_timestamp
                where order_id = ?
                """,
                event.remainingQuantity(),
                cancelStatus(event, eventType),
                event.reason(),
                event.confirmedAt() == null ? LocalDateTime.now() : event.confirmedAt(),
                event.orderId()
        );
    }

    private String cancelStatus(OrderCancelResultEvent event, String eventType) {
        if ("ORDER_CANCEL_REJECTED".equals(eventType)) {
            return "CANCEL_FAILED";
        }
        String status = normalize(event.status());
        if ("CANCELLED".equals(status)) {
            return "CANCELED";
        }
        if ("CANCELED".equals(status)) {
            return status;
        }
        return "CANCELED";
    }

    private void updatePortfolioCashByCancelResult(OrderCancelResultEvent event, OrderReference order) {
        long contestId = defaultContestId(order.contestId());
        PortfolioSnapshotState current = findPortfolioSnapshot(order.memberId(), contestId)
                .orElse(PortfolioSnapshotState.empty());
        BigDecimal cashBalance = nonNull(event.updatedDeposit(), current.cashBalance());
        BigDecimal availableCash = nonNull(event.updatedAvailableBalance(), current.availableCash());
        BigDecimal stockEvaluationAmount = value(current.stockEvaluationAmount());
        BigDecimal totalAsset = cashBalance.add(stockEvaluationAmount);
        Long portfolioVersion = current.portfolioVersion() == null ? 1L : current.portfolioVersion() + 1L;

        jdbcTemplate.update("""
                insert into portfolio_snapshot (
                    member_id,
                    contest_id,
                    account_id,
                    cash_balance,
                    available_cash,
                    stock_evaluation_amount,
                    total_asset,
                    total_buy_amount,
                    total_sell_amount,
                    profit_amount,
                    profit_rate,
                    holdings_json,
                    portfolio_version,
                    onprem_updated_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    account_id = coalesce(values(account_id), account_id),
                    cash_balance = values(cash_balance),
                    available_cash = values(available_cash),
                    stock_evaluation_amount = values(stock_evaluation_amount),
                    total_asset = values(total_asset),
                    total_buy_amount = values(total_buy_amount),
                    total_sell_amount = values(total_sell_amount),
                    profit_amount = values(profit_amount),
                    profit_rate = values(profit_rate),
                    portfolio_version = values(portfolio_version),
                    onprem_updated_at = values(onprem_updated_at),
                    synced_at = current_timestamp
                """,
                order.memberId(),
                contestId,
                event.accountId(),
                cashBalance,
                availableCash,
                stockEvaluationAmount,
                totalAsset,
                value(current.totalBuyAmount()),
                value(current.totalSellAmount()),
                value(current.profitAmount()),
                value(current.profitRate()),
                current.holdingsJson() == null ? "[]" : current.holdingsJson(),
                portfolioVersion,
                event.confirmedAt() == null ? LocalDateTime.now() : event.confirmedAt()
        );
    }

    private void evictReservationCache(long memberId, long contestId, long orderId) {
        redisTemplate.delete(List.of(
                balanceKey(memberId, contestId),
                pendingReleaseKey(memberId, contestId),
                cancelPendingKey(orderId)
        ));
    }

    private void upsertPortfolioSnapshot(PortfolioSyncRequest request) {
        upsertPortfolioSnapshot(request, null);
    }

    private void upsertPortfolioSnapshot(PortfolioSyncRequest request, Long accountId) {
        jdbcTemplate.update("""
                insert into portfolio_snapshot (
                    member_id,
                    contest_id,
                    account_id,
                    cash_balance,
                    available_cash,
                    stock_evaluation_amount,
                    total_asset,
                    total_buy_amount,
                    total_sell_amount,
                    profit_amount,
                    profit_rate,
                    holdings_json,
                    portfolio_version,
                    onprem_updated_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                on duplicate key update
                    account_id = coalesce(values(account_id), account_id),
                    cash_balance = values(cash_balance),
                    available_cash = values(available_cash),
                    stock_evaluation_amount = values(stock_evaluation_amount),
                    total_asset = values(total_asset),
                    total_buy_amount = values(total_buy_amount),
                    total_sell_amount = values(total_sell_amount),
                    profit_amount = values(profit_amount),
                    profit_rate = values(profit_rate),
                    holdings_json = values(holdings_json),
                    portfolio_version = values(portfolio_version),
                    onprem_updated_at = values(onprem_updated_at),
                    synced_at = current_timestamp
                """,
                request.memberId(),
                defaultContestId(request.contestId()),
                accountId,
                request.cashBalance(),
                request.availableCash(),
                request.stockEvaluationAmount(),
                request.totalAsset(),
                request.totalBuyAmount(),
                request.totalSellAmount(),
                request.profitAmount(),
                request.profitRate(),
                request.holdingsJson(),
                request.portfolioVersion(),
                request.onpremUpdatedAt()
        );
    }

    private void upsertMemberSnapshot(MemberCommandResultEvent event) {
        requireMemberId(event);
        MemberCommandPayload payload = event.payload();
        if (payload == null) {
            throw new IllegalArgumentException("payload is required for " + event.eventType());
        }
        if (payload.loginId() == null || payload.loginId().isBlank()) {
            throw new IllegalArgumentException("payload.loginId is required for " + event.eventType());
        }
        if (payload.email() == null || payload.email().isBlank()) {
            throw new IllegalArgumentException("payload.email is required for " + event.eventType());
        }
        if (payload.nickname() == null || payload.nickname().isBlank()) {
            throw new IllegalArgumentException("payload.nickname is required for " + event.eventType());
        }
        LocalDateTime createdAt = payload.createdAt() == null ? eventTime(event) : payload.createdAt();
        LocalDateTime updatedAt = payload.updatedAt() == null ? createdAt : payload.updatedAt();
        jdbcTemplate.update("""
                insert into member_snapshot (
                    member_id,
                    login_id,
                    email,
                    nickname,
                    phone,
                    status,
                    login_fail_count,
                    email_verified,
                    profile_image_url,
                    created_at,
                    updated_at,
                    synced_at
                )
                values (?, ?, ?, ?, ?, ?, 0, true, ?, ?, ?, current_timestamp)
                on duplicate key update
                    login_id = values(login_id),
                    email = values(email),
                    nickname = values(nickname),
                    phone = values(phone),
                    status = values(status),
                    email_verified = true,
                    profile_image_url = values(profile_image_url),
                    updated_at = values(updated_at),
                    synced_at = current_timestamp
                """,
                event.memberId(),
                payload.loginId(),
                payload.email(),
                payload.nickname(),
                payload.phone(),
                normalizeStatus(payload.status()),
                payload.profileImageUrl(),
                createdAt,
                updatedAt
        );
    }

    private void updateMemberSnapshot(MemberCommandResultEvent event) {
        requireMemberId(event);
        MemberCommandPayload payload = event.payload();
        if (payload == null) {
            throw new IllegalArgumentException("payload is required for " + event.eventType());
        }
        jdbcTemplate.update("""
                update member_snapshot
                set login_id = coalesce(?, login_id),
                    email = coalesce(?, email),
                    nickname = coalesce(?, nickname),
                    phone = coalesce(?, phone),
                    status = coalesce(?, status),
                    profile_image_url = coalesce(?, profile_image_url),
                    updated_at = ?,
                    synced_at = current_timestamp
                where member_id = ?
                """,
                blankToNull(payload.loginId()),
                blankToNull(payload.email()),
                blankToNull(payload.nickname()),
                blankToNull(payload.phone()),
                blankToNull(normalizeStatusOrNull(payload.status())),
                blankToNull(payload.profileImageUrl()),
                payload.updatedAt() == null ? eventTime(event) : payload.updatedAt(),
                event.memberId()
        );
    }

    private void markMemberDeleted(MemberCommandResultEvent event) {
        requireMemberId(event);
        jdbcTemplate.update("""
                update member_snapshot
                set status = 'DELETED',
                    updated_at = ?,
                    synced_at = current_timestamp
                where member_id = ?
                """,
                eventTime(event),
                event.memberId()
        );
    }

    private String findProcessStatus(String eventId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select process_status
                    from sync_event_log
                    where event_id = ?
                    """, String.class, eventId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void upsertSyncEventLog(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String processStatus,
            String failureReason,
            LocalDateTime processedAt
    ) {
        jdbcTemplate.update("""
                insert into sync_event_log (
                    event_id,
                    event_type,
                    aggregate_type,
                    aggregate_id,
                    process_status,
                    failure_reason,
                    received_at,
                    processed_at
                )
                values (?, ?, ?, ?, ?, ?, current_timestamp, ?)
                on duplicate key update
                    event_type = values(event_type),
                    aggregate_type = values(aggregate_type),
                    aggregate_id = values(aggregate_id),
                    process_status = values(process_status),
                    failure_reason = values(failure_reason),
                    processed_at = values(processed_at)
                """,
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                processStatus,
                failureReason,
                processedAt
        );
    }

    private long defaultContestId(Long contestId) {
        return contestId == null ? 0L : contestId;
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nonNull(BigDecimal preferred, BigDecimal fallback) {
        return preferred == null ? value(fallback) : preferred;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private String normalizeStatus(String value) {
        return value == null || value.isBlank() ? "ACTIVE" : normalize(value);
    }

    private String normalizeStatusOrNull(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String balanceKey(long memberId, long contestId) {
        return "balance:" + memberId + ":" + contestId;
    }

    private String pendingReleaseKey(long memberId, long contestId) {
        return "balance:pending-release:" + memberId + ":" + contestId;
    }

    private String cancelPendingKey(long orderId) {
        return "cancel:pending:" + orderId;
    }

    private LocalDateTime eventTime(MemberCommandResultEvent event) {
        return event.occurredAt() == null ? LocalDateTime.now() : event.occurredAt();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    private record OrderReference(
            Long memberId,
            Long contestId,
            Long stockId,
            String stockCode,
            String stockName,
            String side
    ) {
    }

    private record StockReference(Long stockId, String stockCode, String stockName) {
    }

    private record PortfolioSnapshotState(
            BigDecimal cashBalance,
            BigDecimal availableCash,
            BigDecimal stockEvaluationAmount,
            BigDecimal totalBuyAmount,
            BigDecimal totalSellAmount,
            BigDecimal profitAmount,
            BigDecimal profitRate,
            String holdingsJson,
            Long portfolioVersion
    ) {
        private static PortfolioSnapshotState empty() {
            return new PortfolioSnapshotState(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "[]",
                    0L
            );
        }
    }

    private record HoldingPosition(String stockCode, int quantity, BigDecimal avgPrice) {
    }
}
