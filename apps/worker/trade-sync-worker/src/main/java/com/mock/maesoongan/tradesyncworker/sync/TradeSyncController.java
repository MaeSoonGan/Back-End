package com.mock.maesoongan.tradesyncworker.sync;

import com.mock.maesoongan.tradesyncworker.common.ApiResponse;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.OrderSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.PortfolioSyncRequest;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.SyncResult;
import com.mock.maesoongan.tradesyncworker.sync.SyncDtos.TradeSyncRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "Internal Trade Sync", description = "Internal trade data sync worker API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/internal/sync")
public class TradeSyncController {

    private final TradeSyncService tradeSyncService;

    public TradeSyncController(TradeSyncService tradeSyncService) {
        this.tradeSyncService = tradeSyncService;
    }

    @Operation(summary = "Get trade sync worker health")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "service", "trade-sync-worker",
                "checkedAt", LocalDateTime.now()
        ));
    }

    @Operation(summary = "Sync order snapshot")
    @PostMapping("/orders")
    public ApiResponse<SyncResult> syncOrder(@Valid @RequestBody OrderSyncRequest request) {
        return ApiResponse.success(tradeSyncService.syncOrder(request));
    }

    @Operation(summary = "Sync trade history")
    @PostMapping("/trades")
    public ApiResponse<SyncResult> syncTrade(@Valid @RequestBody TradeSyncRequest request) {
        return ApiResponse.success(tradeSyncService.syncTrade(request));
    }

    @Operation(summary = "Sync portfolio snapshot")
    @PostMapping("/portfolio")
    public ApiResponse<SyncResult> syncPortfolio(@Valid @RequestBody PortfolioSyncRequest request) {
        return ApiResponse.success(tradeSyncService.syncPortfolio(request));
    }
}
