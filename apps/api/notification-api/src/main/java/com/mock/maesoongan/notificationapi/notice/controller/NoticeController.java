package com.mock.maesoongan.notificationapi.notice.controller;

import com.mock.maesoongan.notificationapi.common.ApiResponse;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeDetailResponse;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeListResponse;
import com.mock.maesoongan.notificationapi.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notices", description = "User notice API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "Get notice list")
    @GetMapping
    public ApiResponse<NoticeListResponse> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        return ApiResponse.success(noticeService.getNotices(page, size));
    }

    @Operation(summary = "Get notice detail")
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getNotice(@PathVariable Long noticeId) {
        return ApiResponse.success(noticeService.getNotice(noticeId));
    }
}
