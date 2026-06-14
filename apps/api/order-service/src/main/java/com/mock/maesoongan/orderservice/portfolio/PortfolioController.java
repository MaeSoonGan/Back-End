package com.mock.maesoongan.orderservice.portfolio;

import com.mock.maesoongan.orderservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.orderservice.common.ApiResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.AvailableCashResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ContestAccountResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingDetailResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.PortfolioSummaryResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ProfitHistoryItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.SeedMoneyResetRequest;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.SeedMoneyResetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Portfolio", description = "Portfolio and contest account API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final CurrentMemberProvider currentMemberProvider;
    private final JdbcTemplate jdbcTemplate;

    public PortfolioController(PortfolioService portfolioService, CurrentMemberProvider currentMemberProvider, JdbcTemplate jdbcTemplate) {
        this.portfolioService = portfolioService;
        this.currentMemberProvider = currentMemberProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "접속 하트비트", description = "로그인 사용자가 주기적으로 호출해 현재 접속자 집계용 last_seen_at을 갱신한다.")
    @PostMapping("/portfolio/heartbeat")
    public ApiResponse<Void> heartbeat() {
        jdbcTemplate.update("""
                insert into online_member (member_id, last_seen_at)
                values (?, now())
                on duplicate key update last_seen_at = now()
                """, currentMemberProvider.memberId());
        return ApiResponse.success(null);
    }

    @Operation(summary = "Get portfolio summary")
    @GetMapping("/portfolio/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary() {
        return ApiResponse.success(portfolioService.getSummary(currentMemberProvider.memberId()));
    }

    @Operation(summary = "Get available cash")
    @GetMapping("/portfolio/available-cash")
    public ApiResponse<AvailableCashResponse> getAvailableCash() {
        return ApiResponse.success(portfolioService.getAvailableCash(currentMemberProvider.memberId()));
    }

    @Operation(summary = "Reset seed money")
    @PostMapping("/portfolio/seed-money/reset")
    public ApiResponse<SeedMoneyResetResponse> resetSeedMoney(@RequestBody SeedMoneyResetRequest request) {
        return ApiResponse.success(portfolioService.resetSeedMoney(currentMemberProvider.memberId(), request));
    }

    @Operation(summary = "Get holdings")
    @GetMapping("/portfolio/holdings")
    public ApiResponse<List<HoldingItem>> getHoldings(
            @RequestParam(required = false) Long contestId
    ) {
        return ApiResponse.success(portfolioService.getHoldings(currentMemberProvider.memberId(), contestId));
    }

    @Operation(summary = "Get holding by stock code")
    @GetMapping("/portfolio/holdings/{stockCode}")
    public ApiResponse<HoldingDetailResponse> getHolding(
            @PathVariable String stockCode,
            @RequestParam(required = false) Long contestId
    ) {
        return ApiResponse.success(portfolioService.getHolding(currentMemberProvider.memberId(), stockCode, contestId));
    }

    @Operation(summary = "Get profit history")
    @GetMapping("/portfolio/profit-history")
    public ApiResponse<List<ProfitHistoryItem>> getProfitHistory(
            @RequestParam(defaultValue = "1M") String period
    ) {
        return ApiResponse.success(portfolioService.getProfitHistory(currentMemberProvider.memberId(), period));
    }

    @Operation(summary = "Get contest account")
    @GetMapping("/contests/{contestId}/account")
    public ApiResponse<ContestAccountResponse> getContestAccount(@PathVariable long contestId) {
        return ApiResponse.success(portfolioService.getContestAccount(currentMemberProvider.memberId(), contestId));
    }
}
