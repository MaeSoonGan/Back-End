package com.mock.maesoongan.marketservice.market;

import com.mock.maesoongan.marketservice.common.ApiResponse;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketIndexResponse;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketRankingItem;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Market", description = "Market information API")
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @Operation(summary = "Get market index")
    @GetMapping("/index")
    public ApiResponse<MarketIndexResponse> getMarketIndex(@RequestParam String market) {
        return ApiResponse.success(marketService.getMarketIndex(market));
    }

    @Operation(summary = "Get market status")
    @GetMapping("/status")
    public ApiResponse<MarketStatusResponse> getMarketStatus() {
        return ApiResponse.success(marketService.getMarketStatus());
    }

    @Operation(summary = "Get market ranking")
    @GetMapping("/ranking")
    public ApiResponse<List<MarketRankingItem>> getMarketRanking(
            @RequestParam(defaultValue = "\uAC70\uB798\uB300\uAE08") String type
    ) {
        return ApiResponse.success(marketService.getMarketRanking(type));
    }
}
