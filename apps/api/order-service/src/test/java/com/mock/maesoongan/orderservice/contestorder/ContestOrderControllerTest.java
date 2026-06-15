package com.mock.maesoongan.orderservice.contestorder;

import com.mock.maesoongan.orderservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.orderservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.orderservice.order.OrderDtos.CancelOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderRequest;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderItem;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeSummary;
import com.mock.maesoongan.orderservice.order.OrderService;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ContestAccountResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ContestOrderControllerTest {

    private OrderService orderService;
    private PortfolioService portfolioService;
    private CurrentMemberProvider currentMemberProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        portfolioService = mock(PortfolioService.class);
        currentMemberProvider = mock(CurrentMemberProvider.class);
        mockMvc = standaloneSetup(new ContestOrderController(orderService, portfolioService, currentMemberProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAccountReturnsContestAccount() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getContestAccount(7L, 3L)).thenReturn(contestAccount());

        mockMvc.perform(get("/api/contest-orders/contests/{contestId}/account", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestId", is(3)))
                .andExpect(jsonPath("$.data.availableBalance", is(7000000)));
    }

    @Test
    void getPortfolioReturnsContestAccountSummary() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getContestAccount(7L, 3L)).thenReturn(contestAccount());

        mockMvc.perform(get("/api/contest-orders/contests/{contestId}/portfolio", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestId", is(3)));
    }

    @Test
    void getHoldingsReturnsContestHoldings() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(portfolioService.getHoldings(7L, 3L)).thenReturn(List.of(new HoldingItem(
                "005930",
                "Samsung Electronics",
                10L,
                new BigDecimal("70000"),
                new BigDecimal("80000"),
                new BigDecimal("800000"),
                new BigDecimal("100000"),
                new BigDecimal("14.2857")
        )));

        mockMvc.perform(get("/api/contest-orders/contests/{contestId}/holdings", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stockCode", is("005930")));
    }

    @Test
    void createOrderUsesContestIdFromPath() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.createOrder(any(Long.class), any())).thenReturn(new CreateOrderResponse(
                1001L,
                "PENDING",
                "Order accepted",
                LocalDateTime.of(2026, 6, 10, 10, 0)
        ));

        mockMvc.perform(post("/api/contest-orders/contests/{contestId}/orders", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": 1,
                                  "contestId": 0,
                                  "stockCode": "005930",
                                  "side": "BUY",
                                  "orderType": "LIMIT",
                                  "price": 75400,
                                  "quantity": 10
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.orderId", is(1001)));

        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(any(Long.class), captor.capture());
        assertThat(captor.getValue().contestId()).isEqualTo(3L);
    }

    @Test
    void getOrdersUsesContestIdFromPath() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.getOrders(any(Long.class), any(), any(), any(), any(), any())).thenReturn(new OrderListResponse(
                List.of(new OrderItem(
                        1001L,
                        3L,
                        "005930",
                        "Samsung Electronics",
                        "BUY",
                        "LIMIT",
                        10L,
                        10L,
                        new BigDecimal("75400"),
                        "PENDING",
                        "1001",
                        LocalDateTime.of(2026, 6, 10, 10, 0)
                )),
                0,
                20,
                1,
                false
        ));

        mockMvc.perform(get("/api/contest-orders/contests/{contestId}/orders", 3L)
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contestId", is(3)));

        verify(orderService).getOrders(7L, 3L, "PENDING", null, 0, 20);
    }

    @Test
    void cancelOrderUsesExistingOrderCancellation() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.cancelOrder(7L, 1001L)).thenReturn(new CancelOrderResponse(
                1001L,
                "CANCEL_REQUESTED",
                "Cancel requested",
                LocalDateTime.of(2026, 6, 10, 10, 5)
        ));

        mockMvc.perform(delete("/api/contest-orders/contests/{contestId}/orders/{orderId}", 3L, 1001L))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.orderId", is(1001)))
                .andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));

        verify(orderService).cancelOrder(7L, 1001L);
    }

    @Test
    void getTradesUsesContestIdFromPath() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.getTrades(any(Long.class), any(), any(), any(), any(), any(), any())).thenReturn(new TradeListResponse(
                new TradeSummary(10L, 0L, new BigDecimal("754000")),
                List.of(),
                0,
                20,
                0,
                false
        ));

        mockMvc.perform(get("/api/contest-orders/contests/{contestId}/trades", 3L)
                        .param("side", "BUY")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.buyQuantity", is(10)));

        verify(orderService).getTrades(7L, 3L, null, null, "BUY", 0, 20);
    }

    private ContestAccountResponse contestAccount() {
        return new ContestAccountResponse(
                3L,
                new BigDecimal("10000000"),
                new BigDecimal("11000000"),
                new BigDecimal("8000000"),
                new BigDecimal("7000000"),
                new BigDecimal("1000000"),
                new BigDecimal("1000000"),
                new BigDecimal("10.00"),
                5,
                100L
        );
    }
}
