package com.mock.maesoongan.auth.dto;

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
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,}$") String password,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^.{2,10}$") String nickname,
            @NotBlank @Pattern(regexp = "^010-\\d{4}-\\d{4}$") String phone,
            @NotNull @AssertTrue Boolean termsAgreed,
            @NotNull @AssertTrue Boolean privacyAgreed,
            Boolean marketingAgreed
    ) {
    }

    public record RegisterResponse(String userId, String nickname, String email) {
    }

    public record FindIdRequest(
            @NotBlank @Email String email,
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
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,}$") String newPassword,
            @NotBlank String newPasswordConfirm
    ) {
    }

    public record ResetPasswordResponse(String maskedUserId, String changedAt) {
    }
}
