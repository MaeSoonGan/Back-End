package com.mock.maesoongan.marketservice.stock;

import com.mock.maesoongan.marketservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.marketservice.common.ApiResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockChartResponse;
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
    private final StockChartService stockChartService;
    private final CurrentMemberProvider currentMemberProvider;

    public StockController(
            StockService stockService,
            StockChartService stockChartService,
            CurrentMemberProvider currentMemberProvider
    ) {
        this.stockService = stockService;
        this.stockChartService = stockChartService;
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

    @Operation(
            summary = "종목 차트 캔들 조회",
            description = "RDS의 stock_daily_price에서 차트 데이터를 조회합니다. 부족한 과거 일봉 데이터는 KIS 국내주식기간별시세 API로 가져와 RDS에 저장한 뒤 응답합니다. period는 D/W/M을 지원하며 W/M은 일봉 데이터를 집계해서 반환합니다."
    )
    @GetMapping("/{code}/chart")
    public ApiResponse<StockChartResponse> getChart(
            @PathVariable String code,
            @RequestParam(defaultValue = "D") String period,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ApiResponse.success(stockChartService.getChart(code, period, range, from, to));
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
