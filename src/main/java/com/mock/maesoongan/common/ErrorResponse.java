package com.mock.maesoongan.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
        @Schema(description = "요청 성공 여부", example = "false")
        boolean success,

        @Schema(description = "에러 코드", example = "BAD_REQUEST")
        String code,

        @Schema(description = "에러 메시지", example = "잘못된 요청입니다.")
        String message,

        @Schema(description = "에러 발생 시각", example = "2025-05-08T14:32:00")
        LocalDateTime timestamp
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, LocalDateTime.now());
    }
}
