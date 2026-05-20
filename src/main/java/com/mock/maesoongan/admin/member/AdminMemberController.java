package com.mock.maesoongan.admin.member;

import com.mock.maesoongan.admin.member.AdminMemberDtos.AddMemberRequest;
import com.mock.maesoongan.admin.member.AdminMemberDtos.AddMemberResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSeedMoneyRequest;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSeedMoneyResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSuspendRequest;
import com.mock.maesoongan.admin.member.AdminMemberDtos.BatchSuspendResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberDetailResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberPageResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.MemberSummaryResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.SeedMoneyRequest;
import com.mock.maesoongan.admin.member.AdminMemberDtos.SeedMoneyResponse;
import com.mock.maesoongan.admin.member.AdminMemberDtos.SuspendMemberResponse;
import com.mock.maesoongan.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Members", description = "관리자 회원 관리 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    public AdminMemberController(AdminMemberService adminMemberService) {
        this.adminMemberService = adminMemberService;
    }

    @Operation(summary = "회원 요약 카드 조회")
    @GetMapping("/summary")
    public ApiResponse<MemberSummaryResponse> getSummary() {
        return ApiResponse.success(adminMemberService.getSummary());
    }

    @Operation(summary = "회원 목록 조회")
    @GetMapping
    public ApiResponse<MemberPageResponse> getMembers(
            @Parameter(description = "닉네임/이메일/아이디 통합 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "상태 필터: ALL / ACTIVE / SUSPENDED / TODAY")
            @RequestParam(required = false) String status,
            @Parameter(description = "가입일 시작 날짜")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "가입일 종료 날짜")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam @Min(0) int page,
            @RequestParam @Min(1) int size
    ) {
        return ApiResponse.success(adminMemberService.getMembers(keyword, status, startDate, endDate, page, size));
    }

    @Operation(summary = "회원 상세 조회")
    @GetMapping("/{userId}")
    public ApiResponse<MemberDetailResponse> getMember(@PathVariable long userId) {
        return ApiResponse.success(adminMemberService.getMember(userId));
    }

    @Operation(summary = "회원 직접 추가")
    @PostMapping
    public ResponseEntity<ApiResponse<AddMemberResponse>> addMember(@Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity
                .status(201)
                .body(ApiResponse.success(adminMemberService.addMember(request)));
    }

    @Operation(summary = "CSV 내보내기")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        byte[] csv = adminMemberService.exportCsv(keyword, status, startDate, endDate);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("admin-members.csv")
                                .build()
                                .toString())
                .body(csv);
    }

    @Operation(summary = "개별 계정 정지")
    @PatchMapping("/{userId}/suspend")
    public ApiResponse<SuspendMemberResponse> suspendMember(@PathVariable long userId) {
        return ApiResponse.success(adminMemberService.suspendMember(userId));
    }

    @Operation(summary = "일괄 계정 정지")
    @PatchMapping("/suspend")
    public ApiResponse<BatchSuspendResponse> suspendMembers(@Valid @RequestBody BatchSuspendRequest request) {
        return ApiResponse.success(adminMemberService.suspendMembers(request.userIds()));
    }

    @Operation(summary = "개별 시드머니 지급")
    @PostMapping("/{userId}/seed-money")
    public ApiResponse<SeedMoneyResponse> paySeedMoney(
            @PathVariable long userId,
            @Valid @RequestBody SeedMoneyRequest request
    ) {
        return ApiResponse.success(adminMemberService.paySeedMoney(userId, request.seedAmount()));
    }

    @Operation(summary = "일괄 시드머니 지급")
    @PostMapping("/seed-money")
    public ApiResponse<BatchSeedMoneyResponse> paySeedMoneyToMembers(
            @Valid @RequestBody BatchSeedMoneyRequest request
    ) {
        return ApiResponse.success(adminMemberService.paySeedMoneyToMembers(request.userIds(), request.seedAmount()));
    }
}
