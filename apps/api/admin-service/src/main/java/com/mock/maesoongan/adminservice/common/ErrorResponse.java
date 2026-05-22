package com.mock.maesoongan.adminservice.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Common error response")
public record ErrorResponse(
        @Schema(description = "Request success", example = "false")
        boolean success,

        @Schema(description = "Error code", example = "BAD_REQUEST")
        String code,

        @Schema(description = "Error message", example = "Invalid request")
        String message,

        @Schema(description = "Error timestamp")
        LocalDateTime timestamp
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, LocalDateTime.now());
    }
}
