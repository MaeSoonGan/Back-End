package com.mock.maesoongan.realtimequoteingestor.market.api;

import com.mock.maesoongan.realtimequoteingestor.market.application.MarketPriceService;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketStatusService;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPricesResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
        name = "서비스용 시세 API",
        description = "다른 마이크로서비스나 화면 초기 로딩에서 사용하는 최신 시세 조회 API입니다."
)
public class MarketRestController {

    private final MarketPriceService marketPriceService;
    private final MarketStatusService marketStatusService;

    public MarketRestController(MarketPriceService marketPriceService, MarketStatusService marketStatusService) {
        this.marketPriceService = marketPriceService;
        this.marketStatusService = marketStatusService;
    }

    @GetMapping("/api/market/price/{stockCode}")
    @Operation(
            summary = "단일 종목 최신 현재가 조회",
            description = "Redis에 저장된 특정 종목의 최신 현재가, 등락, 거래량, 고가/저가/시가 정보를 조회합니다. 다른 서비스가 단건 시세가 필요할 때 사용하는 기본 API입니다."
    )
    public MarketPriceResponse price(
            @Parameter(description = "6자리 종목 코드", example = "005930")
            @PathVariable String stockCode
    ) {
        return marketPriceService.getPrice(stockCode);
    }

    @GetMapping("/api/market/prices")
    @Operation(
            summary = "복수 종목 최신 현재가 일괄 조회",
            description = "쉼표로 구분된 종목 코드 목록을 받아 Redis에 저장된 최신 현재가를 한 번에 조회합니다. 관심종목, 랭킹, 포트폴리오 화면처럼 여러 종목 시세가 필요한 경우 사용합니다."
    )
    public MarketPricesResponse prices(
            @Parameter(description = "쉼표로 구분한 6자리 종목 코드 목록", example = "005930,000660")
            @RequestParam String codes
    ) {
        return marketPriceService.getPrices(codes);
    }

    @GetMapping("/api/market/status")
    @Operation(
            summary = "국내 주식 장 상태 조회",
            description = "서버의 KST 기준 현재 시간이 정규장, 장전, 장후, 마감 중 어디에 해당하는지 반환합니다. 주문 가능 여부나 화면 상태 표시에서 사용할 수 있습니다."
    )
    public MarketStatusResponse status() {
        return marketStatusService.currentStatus();
    }
}
