package com.mock.maesoongan.orderservice.order;

import com.mock.maesoongan.orderservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.orderservice.common.ApiResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CancelOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderRequest;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Orders", description = "Order acceptance API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final CurrentMemberProvider currentMemberProvider;

    public OrderController(OrderService orderService, CurrentMemberProvider currentMemberProvider) {
        this.orderService = orderService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @Operation(summary = "Create order")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResponse response = orderService.createOrder(currentMemberProvider.memberId(), request);
        return ApiResponse.success("주문이 접수되었습니다.", response);
    }

    @Operation(summary = "Request order cancellation")
    @DeleteMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrder(@PathVariable long orderId) {
        CancelOrderResponse response = orderService.cancelOrder(currentMemberProvider.memberId(), orderId);
        return ApiResponse.success("주문이 취소 요청되었습니다.", response);
    }

    @Operation(summary = "Request order cancellation")
    @DeleteMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrderByDeleteAction(@PathVariable long orderId) {
        return cancelOrder(orderId);
    }

    @Operation(summary = "Request order cancellation")
    @PostMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrderByPost(@PathVariable long orderId) {
        return cancelOrder(orderId);
    }

    @Operation(summary = "Request order cancellation")
    @org.springframework.web.bind.annotation.PatchMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrderByPatch(@PathVariable long orderId) {
        return cancelOrder(orderId);
    }

    @Operation(summary = "Get orders")
    @GetMapping("/orders")
    public ApiResponse<OrderListResponse> getOrders(
            @RequestParam(required = false) Long contestId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(orderService.getOrders(currentMemberProvider.memberId(), contestId, status, date, page, size));
    }

    @Operation(summary = "Get trade history")
    @GetMapping("/trades")
    public ApiResponse<TradeListResponse> getTrades(
            @RequestParam(required = false) Long contestId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(orderService.getTrades(currentMemberProvider.memberId(), contestId, from, to, side, page, size));
    }
}
