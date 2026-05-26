package com.mock.maesoongan.marketservice.watchlist;

import com.mock.maesoongan.marketservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.marketservice.common.ApiResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.AddWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.DeleteWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.WatchlistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Watchlist", description = "Watchlist API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final CurrentMemberProvider currentMemberProvider;

    public WatchlistController(WatchlistService watchlistService, CurrentMemberProvider currentMemberProvider) {
        this.watchlistService = watchlistService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @Operation(summary = "Get watchlist")
    @GetMapping
    public ApiResponse<WatchlistResponse> getWatchlist(
            @RequestParam(defaultValue = "domestic") String market
    ) {
        return ApiResponse.success(watchlistService.getWatchlist(currentMemberProvider.memberId(), market));
    }

    @Operation(summary = "Add stock to watchlist")
    @PostMapping("/{stockCode}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddWatchlistResponse> addWatchlist(@PathVariable String stockCode) {
        return ApiResponse.success(watchlistService.addWatchlist(currentMemberProvider.memberId(), stockCode));
    }

    @Operation(summary = "Delete stock from watchlist")
    @DeleteMapping("/{stockCode}")
    public ApiResponse<DeleteWatchlistResponse> deleteWatchlist(@PathVariable String stockCode) {
        return ApiResponse.success(watchlistService.deleteWatchlist(currentMemberProvider.memberId(), stockCode));
    }
}
