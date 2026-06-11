package com.mock.maesoongan.adminservice.notice;

import com.mock.maesoongan.adminservice.common.ApiResponse;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeCreateRequest;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeDetailResponse;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeListItem;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeMutationResponse;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.NoticeUpdateRequest;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Notices", description = "Admin notice management API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    public AdminNoticeController(AdminNoticeService adminNoticeService) {
        this.adminNoticeService = adminNoticeService;
    }

    @Operation(summary = "Get paged notice list")
    @GetMapping
    public ApiResponse<PageResponse<NoticeListItem>> getNotices(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.success(adminNoticeService.getNotices(keyword, status, page, size, sort));
    }

    @Operation(summary = "Get notice detail")
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getNotice(@PathVariable long noticeId) {
        return ApiResponse.success(adminNoticeService.getNotice(noticeId));
    }

    @Operation(summary = "Create notice")
    @PostMapping
    public ApiResponse<NoticeMutationResponse> createNotice(@Valid @RequestBody NoticeCreateRequest request) {
        return ApiResponse.success(adminNoticeService.createNotice(request));
    }

    @Operation(summary = "Update notice")
    @PutMapping("/{noticeId}")
    public ApiResponse<NoticeMutationResponse> updateNotice(
            @PathVariable long noticeId,
            @Valid @RequestBody NoticeUpdateRequest request
    ) {
        return ApiResponse.success(adminNoticeService.updateNotice(noticeId, request));
    }

    @Operation(summary = "Delete notice")
    @DeleteMapping("/{noticeId}")
    public ApiResponse<NoticeMutationResponse> deleteNotice(@PathVariable long noticeId) {
        return ApiResponse.success(adminNoticeService.deleteNotice(noticeId));
    }
}
