package com.mock.maesoongan.orderservice.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Common API response")
public record ApiResponse<T>(
        @Schema(description = "Request success", example = "true")
        boolean success,

        @Schema(description = "Response message")
        String message,

        @Schema(description = "Response data")
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "요청이 성공했습니다.", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
}
