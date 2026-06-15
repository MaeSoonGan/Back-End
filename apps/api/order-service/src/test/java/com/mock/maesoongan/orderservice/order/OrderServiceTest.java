package com.mock.maesoongan.orderservice.order;

import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.order.OrderDtos.CancelOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderRequest;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeListResponse;
import com.mock.maesoongan.orderservice.order.OrderEvents.OrderCancelRequestedEvent;
import com.mock.maesoongan.orderservice.order.OrderRepository.OrderRow;
import com.mock.maesoongan.orderservice.order.OrderRepository.TradeRow;
import com.mock.maesoongan.orderservice.order.OrderRepository.TradeSummaryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final Duration CANCEL_PENDING_TTL = Duration.ofMinutes(5);
    private static final Clock MARKET_OPEN_CLOCK = Clock.fixed(
            Instant.parse("2026-06-12T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private OrderRepository orderRepository;
    private BalanceCache balanceCache;
    private OrderEventPublisher orderEventPublisher;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        balanceCache = mock(BalanceCache.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        orderService = new OrderService(orderRepository, balanceCache, orderEventPublisher, CANCEL_PENDING_TTL, MARKET_OPEN_CLOCK);
    }

    @Test
    void cancelOrderMarksCancelRequestedAndReturnsCancelledDisplayStatus() {
        OrderRow order = orderRow("BUY", new BigDecimal("75400"), 3L, "PENDING");
        when(orderRepository.findOrder(7L, 1001L)).thenReturn(Optional.of(order));
        when(orderRepository.markCancelRequested(eq(7L), eq(1001L), any())).thenReturn(1);
        when(orderRepository.findAccountId(7L, 0L)).thenReturn(Optional.of(7001L));

        CancelOrderResponse response = orderService.cancelOrder(7L, 1001L);

        assertThat(response.orderId()).isEqualTo(1001L);
        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(balanceCache).markCancelPending(7L, 0L, 1001L, new BigDecimal("226200"), CANCEL_PENDING_TTL);
        var eventCaptor = forClass(OrderCancelRequestedEvent.class);
        verify(orderEventPublisher).publishOrderCancelRequested(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(1001L);
        assertThat(eventCaptor.getValue().accountId()).isEqualTo(7001L);
        assertThat(eventCaptor.getValue().requestedAt()).isNotNull();
    }

    @Test
    void createOrderPublishesOnPremOrderRequestEvent() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                0L,
                "005930",
                "BUY",
                "LIMIT",
                new BigDecimal("75400"),
                10L
        );
        when(orderRepository.existsActiveContestParticipation(7L, 0L)).thenReturn(true);
        when(orderRepository.findActiveStock(1L, "005930")).thenReturn(Optional.of(
                new OrderRepository.StockRow(1L, "005930", "\uC0BC\uC131\uC804\uC790", new BigDecimal("75400"))
        ));
        when(balanceCache.reserve(7L, 0L, new BigDecimal("754000"))).thenReturn(BalanceCache.ReserveResult.RESERVED);
        when(orderRepository.nextOrderId()).thenReturn(990003L);
        when(orderRepository.findAccountId(7L, 0L)).thenReturn(Optional.of(7001L));

        orderService.createOrder(7L, request);

        var eventCaptor = forClass(OrderEvents.OrderRequestedEvent.class);
        verify(orderEventPublisher).publishOrderRequested(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(990003L);
        assertThat(eventCaptor.getValue().accountId()).isEqualTo(7001L);
        assertThat(eventCaptor.getValue().stockCode()).isEqualTo("005930");
        assertThat(eventCaptor.getValue().stockName()).isEqualTo("\uC0BC\uC131\uC804\uC790");
        assertThat(eventCaptor.getValue().orderType()).isEqualTo("BUY");
        assertThat(eventCaptor.getValue().priceType()).isEqualTo("LIMIT");
        assertThat(eventCaptor.getValue().orderPrice()).isEqualByComparingTo("75400");
        assertThat(eventCaptor.getValue().orderQuantity()).isEqualTo(10L);
    }

    @Test
    void cancelOrderThrowsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findOrder(7L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(7L, 999L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("ORDER_NOT_FOUND");
                });
    }

    @Test
    void cancelOrderThrowsBadRequestWhenOrderCannotBeCanceled() {
        when(orderRepository.findOrder(7L, 1001L)).thenReturn(Optional.of(orderRow("SELL", new BigDecimal("75400"), 0L, "FILLED")));
        when(orderRepository.markCancelRequested(eq(7L), eq(1001L), any())).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancelOrder(7L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("CANNOT_CANCEL_ORDER");
                });
    }

    @Test
    void createOrderThrowsBadRequestWhenSideIsInvalidBeforeMarketValidation() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                0L,
                "005930",
                "HOLD",
                "LIMIT",
                new BigDecimal("75400"),
                10L
        );

        assertThatThrownBy(() -> orderService.createOrder(7L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_SIDE");
                });
    }

    @Test
    void getOrdersReturnsMappedPageAndDisplayStatus() {
        LocalDate date = LocalDate.of(2026, 6, 10);
        when(orderRepository.countOrders(7L, 0L, "CANCELED", date)).thenReturn(21);
        when(orderRepository.findOrders(7L, 0L, "CANCELED", date, 20, 0)).thenReturn(List.of(
                orderRow("BUY", new BigDecimal("75400"), 0L, "CANCELED")
        ));

        OrderListResponse response = orderService.getOrders(7L, 0L, "CANCELLED", date, 0, 20);

        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo("CANCELLED");
    }

    @Test
    void getOrdersDisplaysCancelRequestedAsCancelled() {
        LocalDate date = LocalDate.of(2026, 6, 10);
        when(orderRepository.countOrders(7L, 0L, "ALL", date)).thenReturn(1);
        when(orderRepository.findOrders(7L, 0L, "ALL", date, 20, 0)).thenReturn(List.of(
                orderRow("BUY", new BigDecimal("75400"), 3L, "CANCEL_REQUESTED")
        ));

        OrderListResponse response = orderService.getOrders(7L, 0L, "ALL", date, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo("CANCELLED");
    }

    @Test
    void getOrdersThrowsBadRequestWhenSizeIsInvalid() {
        assertThatThrownBy(() -> orderService.getOrders(7L, null, null, LocalDate.of(2026, 6, 10), 0, 101))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getTradesReturnsSummaryAndItems() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 10);
        when(orderRepository.countTrades(7L, null, from, to, "BUY")).thenReturn(1);
        when(orderRepository.summarizeTrades(7L, null, from, to, "BUY"))
                .thenReturn(new TradeSummaryRow(10L, 0L, new BigDecimal("754000")));
        when(orderRepository.findTrades(7L, null, from, to, "BUY", 20, 0)).thenReturn(List.of(
                new TradeRow(
                        2001L,
                        1001L,
                        7L,
                        0L,
                        1L,
                        "005930",
                        "\uC0BC\uC131\uC804\uC790",
                        "BUY",
                        new BigDecimal("75400"),
                        10L,
                        new BigDecimal("754000"),
                        LocalDateTime.of(2026, 6, 10, 10, 1)
                )
        ));

        TradeListResponse response = orderService.getTrades(7L, null, from, to, "buy", 0, 20);

        assertThat(response.summary().buyQuantity()).isEqualTo(10L);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).netAmount()).isEqualByComparingTo("754000");
    }

    private OrderRow orderRow(String side, BigDecimal price, long remainingQuantity, String status) {
        return new OrderRow(
                1001L,
                7L,
                0L,
                1L,
                "005930",
                "\uC0BC\uC131\uC804\uC790",
                side,
                "LIMIT",
                price,
                10L,
                remainingQuantity,
                status,
                null,
                LocalDateTime.of(2026, 6, 10, 10, 0),
                null
        );
    }
}
