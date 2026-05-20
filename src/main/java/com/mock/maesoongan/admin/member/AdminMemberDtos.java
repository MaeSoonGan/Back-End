package com.mock.maesoongan.admin.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class AdminMemberDtos {

    private AdminMemberDtos() {
    }

    @Schema(description = "회원 요약 카드 응답")
    public record MemberSummaryResponse(
            int totalCount,
            int activeCount,
            int suspendedCount,
            int todayJoinCount
    ) {
    }

    @Schema(description = "회원 목록 응답")
    public record MemberPageResponse(
            List<MemberListItem> content,
            long totalElements,
            int totalPages,
            int currentPage
    ) {
    }

    @Schema(description = "회원 목록 항목")
    public record MemberListItem(
            long userId,
            String nickname,
            String accountId,
            String email,
            String joinDate,
            int contestCount,
            String totalAsset,
            Double profitRate,
            int loginFailCount,
            String status
    ) {
    }

    @Schema(description = "회원 상세 응답")
    public record MemberDetailResponse(
            long userId,
            String nickname,
            String accountId,
            String email,
            String joinDate,
            int contestCount,
            Long totalAsset,
            Double profitRate,
            int loginFailCount,
            String status
    ) {
    }

    @Schema(description = "회원 직접 추가 요청")
    public record AddMemberRequest(
            @NotBlank(message = "닉네임은 필수입니다.")
            String nickname,

            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            String email,

            @NotBlank(message = "비밀번호는 필수입니다.")
            String password
    ) {
    }

    @Schema(description = "회원 직접 추가 응답")
    public record AddMemberResponse(
            long userId,
            String message
    ) {
    }

    @Schema(description = "개별 계정 정지 응답")
    public record SuspendMemberResponse(
            long userId,
            String status,
            String message
    ) {
    }

    @Schema(description = "일괄 계정 정지 요청")
    public record BatchSuspendRequest(
            @NotEmpty(message = "정지할 회원 ID 목록은 필수입니다.")
            List<Long> userIds
    ) {
    }

    @Schema(description = "일괄 계정 정지 응답")
    public record BatchSuspendResponse(
            int suspendedCount,
            String message
    ) {
    }

    @Schema(description = "개별 시드머니 지급 요청")
    public record SeedMoneyRequest(
            @NotNull(message = "시드머니 금액은 필수입니다.")
            @Min(value = 1, message = "시드머니 금액은 1원 이상이어야 합니다.")
            Long seedAmount
    ) {
    }

    @Schema(description = "개별 시드머니 지급 응답")
    public record SeedMoneyResponse(
            long userId,
            long seedAmount,
            String message
    ) {
    }

    @Schema(description = "일괄 시드머니 지급 요청")
    public record BatchSeedMoneyRequest(
            @NotEmpty(message = "지급 대상 회원 ID 목록은 필수입니다.")
            List<Long> userIds,

            @NotNull(message = "시드머니 금액은 필수입니다.")
            @Min(value = 1, message = "시드머니 금액은 1원 이상이어야 합니다.")
            Long seedAmount
    ) {
    }

    @Schema(description = "일괄 시드머니 지급 응답")
    public record BatchSeedMoneyResponse(
            int succeededCount,
            String message
    ) {
    }
}
