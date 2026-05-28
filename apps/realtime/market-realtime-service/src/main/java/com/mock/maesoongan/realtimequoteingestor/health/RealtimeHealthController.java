package com.mock.maesoongan.realtimequoteingestor.health;

import com.mock.maesoongan.realtimequoteingestor.common.ApiResponse;
import com.mock.maesoongan.realtimequoteingestor.quote.application.QuoteIngestionService;
import com.mock.maesoongan.realtimequoteingestor.quote.application.QuoteIngestionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Tag(
        name = "운영/진단용 상태 API",
        description = "서비스 실행 상태와 KIS 시세 수집 상태를 확인하기 위한 운영 진단 API입니다."
)
public class RealtimeHealthController {

    private final QuoteIngestionService quoteIngestionService;

    public RealtimeHealthController(QuoteIngestionService quoteIngestionService) {
        this.quoteIngestionService = quoteIngestionService;
    }

    @GetMapping("/api/health")
    @Operation(
            summary = "서비스 헬스 체크",
            description = "실시간 시세 서비스가 실행 중인지 확인합니다. 로드밸런서, 배포 검증, 수동 상태 확인에 사용할 수 있습니다."
    )
    public ApiResponse<HealthResponse> health() {
        QuoteIngestionStatus status = quoteIngestionService.status();

        return ApiResponse.success(new HealthResponse(
                "market-realtime-service",
                "UP",
                status.quoteSourceMode(),
                LocalDateTime.now()
        ));
    }

    @GetMapping("/api/realtime/status")
    @Operation(
            summary = "실시간 시세 수집 상태 조회",
            description = "KIS 또는 mock 시세 소스 연결 여부, 수신 이벤트 건수, 마지막 수신 시각, 마지막 오류를 조회합니다. 실시간 수집 Pod가 정상 작동 중인지 확인할 때 사용합니다."
    )
    public ApiResponse<QuoteIngestionStatus> status() {
        return ApiResponse.success(quoteIngestionService.status());
    }

    public record HealthResponse(
            String service,
            String status,
            String quoteSourceMode,
            LocalDateTime checkedAt
    ) {
    }
}
