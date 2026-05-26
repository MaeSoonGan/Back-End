package com.mock.maesoongan.marketservice.stock;

import com.mock.maesoongan.marketservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.marketservice.common.ApiResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockDailyInfoResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockOrderbookResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockPriceResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stocks", description = "Stock quote API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;
    private final CurrentMemberProvider currentMemberProvider;

    public StockController(StockService stockService, CurrentMemberProvider currentMemberProvider) {
        this.stockService = stockService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @Operation(summary = "Get stock price")
    @GetMapping("/{code}/price")
    public ApiResponse<StockPriceResponse> getPrice(@PathVariable String code) {
        return ApiResponse.success(stockService.getPrice(code));
    }

    @Operation(summary = "Get stock daily info")
    @GetMapping("/{code}/daily-info")
    public ApiResponse<StockDailyInfoResponse> getDailyInfo(@PathVariable String code) {
        return ApiResponse.success(stockService.getDailyInfo(code));
    }

    @Operation(summary = "Get stock orderbook snapshot")
    @GetMapping("/{code}/orderbook")
    public ApiResponse<StockOrderbookResponse> getOrderbook(@PathVariable String code) {
        return ApiResponse.success(stockService.getOrderbook(code));
    }

    @Operation(summary = "Search stocks")
    @GetMapping("/search")
    public ApiResponse<StockSearchResponse> search(
            @RequestParam String keyword,
            @RequestParam String market
    ) {
        return ApiResponse.success(stockService.search(keyword, market, currentMemberProvider.memberId()));
    }
}
