package com.mock.maesoongan.orderservice.order;

import com.mock.maesoongan.orderservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.orderservice.order.OrderDtos.CancelOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderItem;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeItem;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class OrderControllerTest {

    private OrderService orderService;
    private CurrentMemberProvider currentMemberProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        currentMemberProvider = mock(CurrentMemberProvider.class);
        mockMvc = standaloneSetup(new OrderController(orderService, currentMemberProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createOrderReturnsAccepted() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.createOrder(any(Long.class), any())).thenReturn(new CreateOrderResponse(
                1001L,
                "PENDING",
                "Order accepted",
                LocalDateTime.of(2026, 6, 10, 10, 0)
        ));

        mockMvc.perform(post("/api/orders")
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
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderId", is(1001)))
                .andExpect(jsonPath("$.data.status", is("PENDING")));

        verify(orderService).createOrder(any(Long.class), any());
    }

    @Test
    void createOrderReturnsBadRequestWhenQuantityIsInvalid() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": 1,
                                  "stockCode": "005930",
                                  "side": "BUY",
                                  "orderType": "LIMIT",
                                  "price": 75400,
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void cancelOrderReturnsAccepted() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.cancelOrder(7L, 1001L)).thenReturn(new CancelOrderResponse(
                1001L,
                "CANCEL_REQUESTED",
                "Cancel requested",
                LocalDateTime.of(2026, 6, 10, 10, 5)
        ));

        mockMvc.perform(delete("/api/orders/{orderId}", 1001L))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderId", is(1001)))
                .andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));
    }

    @Test
    void cancelOrderActionPathReturnsAccepted() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.cancelOrder(7L, 1001L)).thenReturn(new CancelOrderResponse(
                1001L,
                "CANCEL_REQUESTED",
                "Cancel requested",
                LocalDateTime.of(2026, 6, 10, 10, 5)
        ));

        mockMvc.perform(post("/api/orders/{orderId}/cancel", 1001L))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));

        mockMvc.perform(patch("/api/orders/{orderId}/cancel", 1001L))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));

        mockMvc.perform(delete("/api/orders/{orderId}/cancel", 1001L))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status", is("CANCEL_REQUESTED")));
    }

    @Test
    void cancelOrderReturnsNotFoundWhenOrderDoesNotExist() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.cancelOrder(7L, 999L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));

        mockMvc.perform(delete("/api/orders/{orderId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));
    }

    @Test
    void getOrdersReturnsPagedOrders() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.getOrders(any(Long.class), any(), any(), any(), any(), any())).thenReturn(new OrderListResponse(
                List.of(new OrderItem(
                        1001L,
                        0L,
                        "005930",
                        "\uC0BC\uC131\uC804\uC790",
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

        mockMvc.perform(get("/api/orders")
                        .param("status", "PENDING")
                        .param("date", "2026-06-10")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderId", is(1001)))
                .andExpect(jsonPath("$.data.totalElements", is(1)));
    }

    @Test
    void getTradesReturnsTradeHistory() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(orderService.getTrades(any(Long.class), any(), any(), any(), any(), any(), any())).thenReturn(new TradeListResponse(
                new TradeSummary(10L, 0L, new BigDecimal("754000")),
                List.of(new TradeItem(
                        2001L,
                        1001L,
                        0L,
                        "005930",
                        "\uC0BC\uC131\uC804\uC790",
                        "BUY",
                        10L,
                        new BigDecimal("75400"),
                        new BigDecimal("754000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("754000"),
                        LocalDateTime.of(2026, 6, 10, 10, 1)
                )),
                0,
                20,
                1,
                false
        ));

        mockMvc.perform(get("/api/trades")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-10")
                        .param("side", "BUY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.buyQuantity", is(10)))
                .andExpect(jsonPath("$.data.content[0].tradeId", is(2001)));
    }
}
