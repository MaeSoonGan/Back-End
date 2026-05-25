package com.mock.maesoongan.adminservice.notice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminNoticeDtos {

    private AdminNoticeDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    public record NoticeListItem(
            long noticeId,
            String title,
            boolean isPinned,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long adminId,
            String adminName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record NoticeDetailResponse(
            long noticeId,
            String title,
            String content,
            boolean isPinned,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long adminId,
            String adminName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(description = "Notice create request")
    public record NoticeCreateRequest(
            @NotBlank(message = "title is required")
            String title,

            @NotBlank(message = "content is required")
            String content,

            Boolean isPinned,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    @Schema(description = "Notice update request")
    public record NoticeUpdateRequest(
            @NotBlank(message = "title is required")
            String title,

            @NotBlank(message = "content is required")
            String content,

            Boolean isPinned,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }

    public record NoticeMutationResponse(
            long noticeId,
            String status,
            String message
    ) {
    }
}
