package com.mock.maesoongan.orderservice.order;

import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.order.BalanceCache.ReserveResult;
import com.mock.maesoongan.orderservice.order.OrderDtos.CancelOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderRequest;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderItem;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeItem;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeSummary;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderCancelRequestedEvent;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderRequestedEvent;
import com.mock.maesoongan.orderservice.order.OrderRepository.OrderInsertCommand;
import com.mock.maesoongan.orderservice.order.OrderRepository.OrderRow;
import com.mock.maesoongan.orderservice.order.OrderRepository.PortfolioRow;
import com.mock.maesoongan.orderservice.order.OrderRepository.StockRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class OrderService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final OrderRepository orderRepository;
    private final BalanceCache balanceCache;
    private final OrderEventPublisher orderEventPublisher;
    private final Duration cancelPendingTtl;
    private final Clock clock;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            BalanceCache balanceCache,
            OrderEventPublisher orderEventPublisher,
            @Value("${app.order.cancel-pending-ttl}") Duration cancelPendingTtl
    ) {
        this(orderRepository, balanceCache, orderEventPublisher, cancelPendingTtl, Clock.system(SEOUL));
    }

    OrderService(
            OrderRepository orderRepository,
            BalanceCache balanceCache,
            OrderEventPublisher orderEventPublisher,
            Duration cancelPendingTtl,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.balanceCache = balanceCache;
        this.orderEventPublisher = orderEventPublisher;
        this.cancelPendingTtl = cancelPendingTtl;
        this.clock = clock;
    }

    @Transactional
    public CreateOrderResponse createOrder(long memberId, CreateOrderRequest request) {
        long contestId = request.contestId() == null ? 0L : request.contestId();
        String stockCode = normalizeStockCode(request.stockCode());
        String side = normalizeSide(request.side());
        String orderType = normalizeOrderType(request.orderType());

        validateMarketOpen();
        if (contestId < 0) {
            throw badRequest("contestId must be greater than or equal to 0");
        }
        if (!orderRepository.existsActiveContestParticipation(memberId, contestId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "대회 참가 상태가 유효하지 않습니다.");
        }

        StockRow stock = orderRepository.findActiveStock(request.stockId(), stockCode)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "종목을 찾을 수 없습니다."));
        BigDecimal orderPrice = resolveOrderPrice(orderType, request.price(), stock.currentPrice());
        BigDecimal reservedAmount = BigDecimal.ZERO;
        boolean reserved = false;

        if ("BUY".equals(side)) {
            reservedAmount = orderPrice.multiply(BigDecimal.valueOf(request.quantity()));
            reserveBalance(memberId, contestId, reservedAmount);
            reserved = true;
        }

        long orderId = orderRepository.nextOrderId();
        LocalDateTime now = LocalDateTime.now(clock);

        try {
            orderRepository.insertOrder(new OrderInsertCommand(
                    orderId,
                    memberId,
                    contestId,
                    stock.stockId(),
                    stock.stockCode(),
                    stock.stockName(),
                    side,
                    orderType,
                    orderPrice,
                    request.quantity(),
                    now
            ));

            orderEventPublisher.publishOrderRequested(new OrderRequestedEvent(
                    orderId,
                    accountId(memberId, contestId),
                    stock.stockCode(),
                    stock.stockName(),
                    side,
                    orderType,
                    eventOrderPrice(orderType, orderPrice),
                    request.quantity(),
                    now
            ));
        } catch (RuntimeException exception) {
            if (reserved) {
                balanceCache.releaseReservation(memberId, contestId, reservedAmount);
            }
            throw exception;
        }

        return new CreateOrderResponse(orderId, "PENDING", "주문이 접수되었습니다.", now);
    }

    @Transactional
    public CancelOrderResponse cancelOrder(long memberId, long orderId) {
        OrderRow order = orderRepository.findOrder(memberId, orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now(clock);
        int updated = orderRepository.markCancelRequested(memberId, orderId, now);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL_ORDER", "취소할 수 없는 주문입니다.");
        }

        BigDecimal pendingReleaseAmount = pendingReleaseAmount(order);
        balanceCache.markCancelPending(memberId, order.contestId(), orderId, pendingReleaseAmount, cancelPendingTtl);

        orderEventPublisher.publishOrderCancelRequested(new OrderCancelRequestedEvent(
                order.orderId(),
                accountId(order.memberId(), order.contestId()),
                now
        ));

        return new CancelOrderResponse(orderId, "CANCEL_REQUESTED", "주문이 취소 요청되었습니다.", now);
    }

    @Transactional(readOnly = true)
    public OrderListResponse getOrders(long memberId, Long contestId, String status, LocalDate date, Integer page, Integer size) {
        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);
        String normalizedStatus = normalizeOrderStatus(status);
        LocalDate resolvedDate = date == null ? LocalDate.now(clock) : date;
        int total = orderRepository.countOrders(memberId, contestId, normalizedStatus, resolvedDate);

        var items = orderRepository.findOrders(
                        memberId,
                        contestId,
                        normalizedStatus,
                        resolvedDate,
                        resolvedSize,
                        resolvedPage * resolvedSize
                )
                .stream()
                .map(row -> new OrderItem(
                        row.orderId(),
                        row.contestId(),
                        row.stockCode(),
                        row.stockName(),
                        row.side(),
                        row.orderType(),
                        row.orderQuantity(),
                        row.remainingQuantity(),
                        row.orderPrice(),
                        displayStatus(row.status()),
                        orderNumber(row.orderId()),
                        row.orderedAt()
                ))
                .toList();

        return new OrderListResponse(items, resolvedPage, resolvedSize, total, hasNext(resolvedPage, resolvedSize, total));
    }

    @Transactional(readOnly = true)
    public TradeListResponse getTrades(long memberId, Long contestId, LocalDate from, LocalDate to, String side, Integer page, Integer size) {
        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);
        LocalDate resolvedTo = to == null ? LocalDate.now(clock) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(30) : from;
        String normalizedSide = normalizeTradeSide(side);
        int total = orderRepository.countTrades(memberId, contestId, resolvedFrom, resolvedTo, normalizedSide);
        var summary = orderRepository.summarizeTrades(memberId, contestId, resolvedFrom, resolvedTo, normalizedSide);
        var items = orderRepository.findTrades(
                        memberId,
                        contestId,
                        resolvedFrom,
                        resolvedTo,
                        normalizedSide,
                        resolvedSize,
                        resolvedPage * resolvedSize
                )
                .stream()
                .map(row -> new TradeItem(
                        row.tradeId(),
                        row.orderId(),
                        row.contestId(),
                        row.stockCode(),
                        row.stockName(),
                        row.side(),
                        row.executedQuantity(),
                        row.executedPrice(),
                        row.executedAmount(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        row.executedAmount(),
                        row.executedAt()
                ))
                .toList();

        return new TradeListResponse(
                new TradeSummary(summary.buyQuantity(), summary.sellQuantity(), summary.totalAmount()),
                items,
                resolvedPage,
                resolvedSize,
                total,
                hasNext(resolvedPage, resolvedSize, total)
        );
    }

    private void reserveBalance(long memberId, long contestId, BigDecimal amount) {
        ReserveResult result = balanceCache.reserve(memberId, contestId, amount);
        if (result == ReserveResult.MISSING) {
            PortfolioRow portfolio = orderRepository.findPortfolio(memberId, contestId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "잔고가 부족합니다."));
            balanceCache.cacheBalance(memberId, contestId, portfolio.availableCash());
            result = balanceCache.reserve(memberId, contestId, amount);
        }
        if (result == ReserveResult.INSUFFICIENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "잔고가 부족합니다.");
        }
        if (result != ReserveResult.RESERVED) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");
        }
    }

    private BigDecimal pendingReleaseAmount(OrderRow order) {
        if (!"BUY".equals(order.side())) {
            return BigDecimal.ZERO;
        }
        return order.orderPrice().multiply(BigDecimal.valueOf(order.remainingQuantity()));
    }

    private BigDecimal resolveOrderPrice(String orderType, BigDecimal requestPrice, BigDecimal currentPrice) {
        if ("LIMIT".equals(orderType)) {
            if (requestPrice == null) {
                throw badRequest("지정가 주문은 가격이 필요합니다.");
            }
            return requestPrice;
        }
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("현재가를 조회할 수 없습니다.");
        }
        return currentPrice;
    }

    private BigDecimal eventOrderPrice(String priceType, BigDecimal orderPrice) {
        return "MARKET".equals(priceType) ? null : orderPrice;
    }

    private long accountId(long memberId, long contestId) {
        return orderRepository.findAccountId(memberId, contestId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "ACCOUNT_NOT_FOUND",
                        "Account id was not found for memberId=" + memberId + ", contestId=" + contestId
                ));
    }

    private String normalizeStockCode(String stockCode) {
        return stockCode.trim().toUpperCase(Locale.ROOT);
    }

    private void validateMarketOpen() {
        LocalTime now = LocalTime.now(clock);
        if (now.isBefore(LocalTime.of(9, 0))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MARKET_CLOSED", "장 운영 시간이 아닙니다.");
        }
    }

    private String normalizeSide(String side) {
        String normalized = side.trim().toUpperCase(Locale.ROOT);
        if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIDE", "잘못된 주문 구분입니다.");
        }
        return normalized;
    }

    private String normalizeOrderType(String orderType) {
        String normalized = orderType.trim().toUpperCase(Locale.ROOT);
        if (!"LIMIT".equals(normalized) && !"MARKET".equals(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_TYPE", "잘못된 주문 유형입니다.");
        }
        return normalized;
    }

    private String normalizeOrderStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ALL";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of(
                "ALL",
                "PENDING",
                "OPEN",
                "PARTIAL",
                "PARTIALLY_FILLED",
                "FILLED",
                "CANCEL_REQUESTED",
                "CANCEL_FAILED",
                "CANCELLED",
                "CANCELED",
                "REJECTED"
        ).contains(normalized)) {
            throw badRequest("status must be ALL, PENDING, OPEN, PARTIAL, PARTIALLY_FILLED, FILLED, CANCEL_REQUESTED, CANCEL_FAILED, CANCELLED, or REJECTED");
        }
        return "CANCELLED".equals(normalized) ? "CANCELED" : normalized;
    }

    private String displayStatus(String status) {
        return "CANCELED".equals(status) ? "CANCELLED" : status;
    }

    private String normalizeTradeSide(String side) {
        if (side == null || side.isBlank()) {
            return "ALL";
        }
        String normalized = side.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("ALL", "BUY", "SELL").contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIDE", "잘못된 주문 구분입니다.");
        }
        return normalized;
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 0) {
            throw badRequest("page must be greater than or equal to 0");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw badRequest("size must be between 1 and 100");
        }
        return size;
    }

    private boolean hasNext(int page, int size, int total) {
        return (page + 1) * size < total;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private String orderNumber(long orderId) {
        return String.valueOf(orderId);
    }
}
