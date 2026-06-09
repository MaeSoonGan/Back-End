package com.mock.maesoongan.notificationapi.notice.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    public record NoticeListResponse(
            List<NoticeItem> content,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    public record NoticeItem(
            Long noticeId,
            String title,
            String content,
            boolean isPinned,
            String authorName,
            LocalDateTime createdAt
    ) {
    }

    public record NoticeDetailResponse(
            Long noticeId,
            String title,
            String content,
            boolean isPinned,
            String authorName,
            LocalDateTime createdAt
    ) {
    }
}
