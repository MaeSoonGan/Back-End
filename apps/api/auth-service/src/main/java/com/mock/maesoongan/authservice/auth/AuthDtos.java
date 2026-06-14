package com.mock.maesoongan.authservice.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String userId,
            @NotBlank String password,
            Boolean keepLogin
    ) {
        public boolean keepLoginValue() {
            return Boolean.TRUE.equals(keepLogin);
        }
    }

    public record TokenResponse(String accessToken, String refreshToken) {
    }

    public record ReissueRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record AvailabilityResponse(boolean available) {
    }

    public record SendEmailCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "signup|find-id|reset-password") String purpose
    ) {
    }

    public record ExpiresInResponse(int expiresIn) {
    }

    public record VerifyCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String code
    ) {
    }

    public record VerifiedResponse(boolean verified) {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{5,20}$") String userId,
            @NotBlank String password,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^.{2,10}$") String nickname,
            @NotBlank @Pattern(regexp = "^010-\\d{4}-\\d{4}$") String phone,
            @NotNull @AssertTrue Boolean termsAgreed,
            @NotNull @AssertTrue Boolean privacyAgreed,
            Boolean marketingAgreed
    ) {
    }

    public record RegisterResponse(String requestId, String userId, String nickname, String email) {
    }

    public record FindIdRequest(
            @NotBlank @Email String email,
            // 아이디 찾기는 이메일 인증만으로 동작(전화번호 선택). 값이 있으면 형식 검증.
            @Pattern(regexp = "^010-\\d{4}-\\d{4}$|^$", message = "올바른 전화번호 형식이 아닙니다") String phone,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String code
    ) {
    }

    public record FindIdResponse(String maskedUserId, String email, String joinedAt) {
    }

    public record VerifyResetRequest(
            @NotBlank String userId,
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String code
    ) {
    }

    public record VerifyResetResponse(String resetToken, String maskedUserId) {
    }

    public record ResetPasswordRequest(
            @NotBlank String resetToken,
            @NotBlank String newPassword,
            @NotBlank String newPasswordConfirm
    ) {
    }

    public record ResetPasswordResponse(String maskedUserId, String changedAt) {
    }

    public record MemberProfileResponse(
            String userId,
            String nickname,
            String phone,
            String email,
            boolean emailVerified,
            String profileImageUrl
    ) {
    }

    public record UpdateMemberProfileRequest(
            @Pattern(regexp = "^.{2,10}$") String nickname,
            @Pattern(regexp = "^010-\\d{4}-\\d{4}$") String phone,
            @Email String email,
            String profileImageUrl
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword,
            @NotBlank String newPasswordConfirm
    ) {
    }

    public record ChangePasswordResponse(String changedAt) {
    }

    public record WithdrawMemberRequest(
            @NotBlank String password
    ) {
    }

    public record WithdrawMemberResponse(String withdrawnAt) {
    }

    public record ProfileImageUploadUrlRequest(
            @NotBlank @Pattern(regexp = "image/(jpeg|png|webp|gif)") String contentType
    ) {
    }

    public record ProfileImageUploadUrlResponse(
            String uploadUrl,
            String imageUrl
    ) {
    }
}
