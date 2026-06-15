package com.mock.maesoongan.orderservice.contestorder;

import com.mock.maesoongan.orderservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.orderservice.common.ApiResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CancelOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderRequest;
import com.mock.maesoongan.orderservice.order.OrderDtos.CreateOrderResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.OrderListResponse;
import com.mock.maesoongan.orderservice.order.OrderDtos.TradeListResponse;
import com.mock.maesoongan.orderservice.order.OrderService;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ContestAccountResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingDetailResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Contest Orders", description = "Contest account and order API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/contest-orders/contests/{contestId}")
public class ContestOrderController {

    private final OrderService orderService;
    private final PortfolioService portfolioService;
    private final CurrentMemberProvider currentMemberProvider;

    public ContestOrderController(
            OrderService orderService,
            PortfolioService portfolioService,
            CurrentMemberProvider currentMemberProvider
    ) {
        this.orderService = orderService;
        this.portfolioService = portfolioService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @Operation(summary = "Get contest account")
    @GetMapping("/account")
    public ApiResponse<ContestAccountResponse> getAccount(@PathVariable long contestId) {
        return ApiResponse.success(portfolioService.getContestAccount(currentMemberProvider.memberId(), contestId));
    }

    @Operation(summary = "Get contest portfolio")
    @GetMapping("/portfolio")
    public ApiResponse<ContestAccountResponse> getPortfolio(@PathVariable long contestId) {
        return ApiResponse.success(portfolioService.getContestAccount(currentMemberProvider.memberId(), contestId));
    }

    @Operation(summary = "Get contest holdings")
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingItem>> getHoldings(@PathVariable long contestId) {
        return ApiResponse.success(portfolioService.getHoldings(currentMemberProvider.memberId(), contestId));
    }

    @Operation(summary = "Get contest holding by stock code")
    @GetMapping("/holdings/{stockCode}")
    public ApiResponse<HoldingDetailResponse> getHolding(
            @PathVariable long contestId,
            @PathVariable String stockCode
    ) {
        return ApiResponse.success(portfolioService.getHolding(currentMemberProvider.memberId(), stockCode, contestId));
    }

    @Operation(summary = "Create contest order")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CreateOrderResponse> createOrder(
            @PathVariable long contestId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CreateOrderRequest contestRequest = new CreateOrderRequest(
                request.stockId(),
                contestId,
                request.stockCode(),
                request.side(),
                request.orderType(),
                request.price(),
                request.quantity()
        );
        CreateOrderResponse response = orderService.createOrder(currentMemberProvider.memberId(), contestRequest);
        return ApiResponse.success("Contest order accepted", response);
    }

    @Operation(summary = "Request contest order cancellation")
    @DeleteMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrder(
            @PathVariable long contestId,
            @PathVariable long orderId
    ) {
        CancelOrderResponse response = orderService.cancelOrder(currentMemberProvider.memberId(), orderId);
        return ApiResponse.success("Contest order cancel requested", response);
    }

    @Operation(summary = "Request contest order cancellation")
    @DeleteMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrderByDeleteAction(
            @PathVariable long contestId,
            @PathVariable long orderId
    ) {
        return cancelOrder(contestId, orderId);
    }

    @Operation(summary = "Request contest order cancellation")
    @PostMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrderByPost(
            @PathVariable long contestId,
            @PathVariable long orderId
    ) {
        return cancelOrder(contestId, orderId);
    }

    @Operation(summary = "Request contest order cancellation")
    @org.springframework.web.bind.annotation.PatchMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CancelOrderResponse> cancelOrderByPatch(
            @PathVariable long contestId,
            @PathVariable long orderId
    ) {
        return cancelOrder(contestId, orderId);
    }

    @Operation(summary = "Get contest orders")
    @GetMapping("/orders")
    public ApiResponse<OrderListResponse> getOrders(
            @PathVariable long contestId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(orderService.getOrders(
                currentMemberProvider.memberId(),
                contestId,
                status,
                date,
                page,
                size
        ));
    }

    @Operation(summary = "Get contest trade history")
    @GetMapping("/trades")
    public ApiResponse<TradeListResponse> getTrades(
            @PathVariable long contestId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(orderService.getTrades(
                currentMemberProvider.memberId(),
                contestId,
                from,
                to,
                side,
                page,
                size
        ));
    }
}
