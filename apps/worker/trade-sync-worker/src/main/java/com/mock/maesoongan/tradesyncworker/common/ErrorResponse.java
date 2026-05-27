package com.mock.maesoongan.tradesyncworker.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response")
public record ErrorResponse(
        @Schema(description = "Request success", example = "false")
        boolean success,

        @Schema(description = "Error code", example = "BAD_REQUEST")
        String code,

        @Schema(description = "Error message", example = "Invalid request")
        String message
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message);
    }
}
