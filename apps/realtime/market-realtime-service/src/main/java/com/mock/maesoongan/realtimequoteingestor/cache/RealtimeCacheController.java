package com.mock.maesoongan.realtimequoteingestor.cache;

import com.mock.maesoongan.realtimequoteingestor.common.ApiResponse;
import com.mock.maesoongan.realtimequoteingestor.stock.StockMetadataCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
        name = "운영/진단용 Redis API",
        description = "ElastiCache 연결 상태와 Redis에 저장된 실시간 시세/종목 마스터 값을 확인하기 위한 운영 진단 API입니다."
)
public class RealtimeCacheController {

    private static final String PRICE_KEY_PREFIX = "stock:";
    private static final String PRICE_KEY_SUFFIX = ":price";
    private static final String ORDERBOOK_KEY_PREFIX = "stock:";
    private static final String ORDERBOOK_KEY_SUFFIX = ":orderbook";

    private final RedisCacheProbe redisCacheProbe;

    public RealtimeCacheController(RedisCacheProbe redisCacheProbe) {
        this.redisCacheProbe = redisCacheProbe;
    }

    @GetMapping("/api/realtime/cache/status")
    @Operation(
            summary = "Redis 연결 상태 확인",
            description = "현재 서비스가 Redis 또는 ElastiCache에 정상 연결 가능한지 PING으로 확인합니다. 배포, 터널링, 보안 그룹 설정 검증에 사용합니다."
    )
    public ApiResponse<RedisCacheProbe.RedisConnectionStatus> status() {
        return ApiResponse.success(redisCacheProbe.status());
    }

    @GetMapping("/api/realtime/cache/price/{stockCode}")
    @Operation(
            summary = "Redis 최신 현재가 원본 확인",
            description = "`stock:{stockCode}:price` 키에 저장된 최신 현재가 JSON 원본을 조회합니다. 일반 서비스 연동보다는 Redis 적재 여부를 확인할 때 사용합니다."
    )
    public ApiResponse<RedisCacheProbe.RedisCacheValue> price(
            @Parameter(description = "6자리 종목 코드", example = "005930")
            @PathVariable String stockCode
    ) {
        return ApiResponse.success(redisCacheProbe.get(PRICE_KEY_PREFIX + stockCode + PRICE_KEY_SUFFIX));
    }

    @GetMapping("/api/realtime/cache/orderbook/{stockCode}")
    @Operation(
            summary = "Redis 최신 호가 원본 확인",
            description = "`stock:{stockCode}:orderbook` 키에 저장된 최신 호가 JSON 원본을 조회합니다. KIS 호가 수신과 Redis 적재 여부를 확인할 때 사용합니다."
    )
    public ApiResponse<RedisCacheProbe.RedisCacheValue> orderbook(
            @Parameter(description = "6자리 종목 코드", example = "005930")
            @PathVariable String stockCode
    ) {
        return ApiResponse.success(redisCacheProbe.get(ORDERBOOK_KEY_PREFIX + stockCode + ORDERBOOK_KEY_SUFFIX));
    }

    @GetMapping("/api/realtime/cache/stock-master")
    @Operation(
            summary = "종목 마스터 Redis 적재 상태 확인",
            description = "`stock:master:status` 키에 저장된 종목 마스터 적재 결과를 조회합니다. `.mst` 파일이 Redis에 정상 적재되었는지 확인할 때 사용합니다."
    )
    public ApiResponse<RedisCacheProbe.RedisCacheValue> stockMaster() {
        return ApiResponse.success(redisCacheProbe.get(StockMetadataCache.STOCK_MASTER_STATUS_KEY));
    }
}
