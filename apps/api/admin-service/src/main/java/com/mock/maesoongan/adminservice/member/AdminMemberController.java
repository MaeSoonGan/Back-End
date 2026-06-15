package com.mock.maesoongan.adminservice.member;

import com.mock.maesoongan.adminservice.common.ApiResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberDetailResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberListItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberSearchItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.MemberSummaryResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.PageResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.ReleaseSuspensionRequest;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.ReleaseSuspensionResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentHistoryItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentRequest;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SeedPaymentSummaryResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspendMembersRequest;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspendMembersResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspensionDetailResponse;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspensionHistoryItem;
import com.mock.maesoongan.adminservice.member.AdminMemberDtos.SuspensionSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Admin Members", description = "Admin member management API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    public AdminMemberController(AdminMemberService adminMemberService) {
        this.adminMemberService = adminMemberService;
    }

    @Operation(summary = "Get member summary cards")
    @GetMapping("/summary")
    public ApiResponse<MemberSummaryResponse> getMemberSummary() {
        return ApiResponse.success(adminMemberService.getMemberSummary());
    }

    @Operation(summary = "Get paged member list")
    @GetMapping
    public ApiResponse<PageResponse<MemberListItem>> getMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.success(adminMemberService.getMembers(keyword, status, startDate, endDate, page, size, sort));
    }

    @Operation(summary = "Search members for admin selector")
    @GetMapping("/search")
    public ApiResponse<List<MemberSearchItem>> searchMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.success(adminMemberService.searchMembers(keyword, limit));
    }

    @Operation(summary = "Export filtered member list as CSV")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return csv("members.csv", adminMemberService.exportMembers(keyword, status, startDate, endDate));
    }

    @Operation(summary = "Get member detail")
    @GetMapping("/{memberId}")
    public ApiResponse<MemberDetailResponse> getMember(@PathVariable long memberId) {
        return ApiResponse.success(adminMemberService.getMember(memberId));
    }

    @Operation(summary = "Suspend one or more members")
    @PatchMapping("/suspend")
    public ApiResponse<SuspendMembersResponse> suspendMembers(@Valid @RequestBody SuspendMembersRequest request) {
        return ApiResponse.success(adminMemberService.suspendMembers(request));
    }

    @Operation(summary = "Get suspension summary cards")
    @GetMapping("/suspensions/summary")
    public ApiResponse<SuspensionSummaryResponse> getSuspensionSummary() {
        return ApiResponse.success(adminMemberService.getSuspensionSummary());
    }

    @Operation(summary = "Get paged suspension history")
    @GetMapping("/suspensions")
    public ApiResponse<PageResponse<SuspensionHistoryItem>> getSuspensions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.success(adminMemberService.getSuspensions(keyword, status, startDate, endDate, page, size, sort));
    }

    @Operation(summary = "Export suspension history as CSV")
    @GetMapping("/suspensions/export")
    public ResponseEntity<byte[]> exportSuspensions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return csv("member-suspensions.csv", adminMemberService.exportSuspensions(keyword, status, startDate, endDate));
    }

    @Operation(summary = "Get suspension detail")
    @GetMapping("/suspensions/{suspensionId}")
    public ApiResponse<SuspensionDetailResponse> getSuspension(@PathVariable long suspensionId) {
        return ApiResponse.success(adminMemberService.getSuspension(suspensionId));
    }

    @Operation(summary = "Release member suspension")
    @PatchMapping("/suspensions/{suspensionId}/release")
    public ApiResponse<ReleaseSuspensionResponse> releaseSuspension(
            @PathVariable long suspensionId,
            @Valid @RequestBody ReleaseSuspensionRequest request
    ) {
        return ApiResponse.success(adminMemberService.releaseSuspension(suspensionId, request));
    }

    @Operation(summary = "Release member suspension by memberId (이름 입력 기반 해제)")
    @PatchMapping("/{memberId}/suspensions/release")
    public ApiResponse<ReleaseSuspensionResponse> releaseSuspensionByMember(
            @PathVariable long memberId,
            @Valid @RequestBody ReleaseSuspensionRequest request
    ) {
        return ApiResponse.success(adminMemberService.releaseSuspensionByMember(memberId, request));
    }

    @Operation(summary = "Get seed money payment summary cards")
    @GetMapping("/seed-payments/summary")
    public ApiResponse<SeedPaymentSummaryResponse> getSeedPaymentSummary() {
        return ApiResponse.success(adminMemberService.getSeedPaymentSummary());
    }

    @Operation(summary = "Pay seed money to one or more members")
    @PostMapping("/seed-payments")
    public ApiResponse<SeedPaymentResponse> paySeedMoney(@Valid @RequestBody SeedPaymentRequest request) {
        return ApiResponse.success(adminMemberService.paySeedMoney(request));
    }

    @Operation(summary = "Get paged seed money payment history")
    @GetMapping("/seed-payments")
    public ApiResponse<PageResponse<SeedPaymentHistoryItem>> getSeedPayments(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.success(adminMemberService.getSeedPayments(keyword, status, startDate, endDate, page, size, sort));
    }

    @Operation(summary = "Export seed money payment history as CSV")
    @GetMapping("/seed-payments/export")
    public ResponseEntity<byte[]> exportSeedPayments(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return csv("member-seed-payments.csv", adminMemberService.exportSeedPayments(keyword, status, startDate, endDate));
    }

    private ResponseEntity<byte[]> csv(String filename, String csv) {
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
