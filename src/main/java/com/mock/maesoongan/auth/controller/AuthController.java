package com.mock.maesoongan.auth.controller;

import com.mock.maesoongan.auth.dto.AuthDtos.AvailabilityResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.ExpiresInResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.FindIdRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.FindIdResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.LoginRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.RegisterRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.RegisterResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.ResetPasswordRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.ResetPasswordResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.SendEmailCodeRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.TokenResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifiedResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifyCodeRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifyResetRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifyResetResponse;
import com.mock.maesoongan.auth.service.AuthService;
import com.mock.maesoongan.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Issue JWT access and refresh tokens.")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/check-id")
    @Operation(summary = "Check userId", description = "Check whether a userId is available.")
    public ApiResponse<AvailabilityResponse> checkId(
            @RequestParam @Pattern(regexp = "^[A-Za-z0-9]{5,20}$") String userId
    ) {
        return ApiResponse.success(authService.checkId(userId));
    }

    @PostMapping("/send-code")
    @Operation(summary = "Send email code", description = "Send a 6-digit email code for signup, find-id, or reset-password.")
    public ApiResponse<ExpiresInResponse> sendCode(@Valid @RequestBody SendEmailCodeRequest request) {
        return ApiResponse.success(authService.sendEmailCode(request));
    }

    @PostMapping("/verify-code")
    @Operation(summary = "Verify email code", description = "Verify a 6-digit email code.")
    public ApiResponse<VerifiedResponse> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        return ApiResponse.success(authService.verifyEmailCode(request));
    }

    @GetMapping("/check-nickname")
    @Operation(summary = "Check nickname", description = "Check whether a nickname is available.")
    public ApiResponse<AvailabilityResponse> checkNickname(
            @RequestParam @Pattern(regexp = "^.{2,10}$") String nickname
    ) {
        return ApiResponse.success(authService.checkNickname(nickname));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register", description = "Create an account after email verification.")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/find-id")
    @Operation(summary = "Find userId", description = "Find a masked userId after email code verification.")
    public ApiResponse<FindIdResponse> findId(@Valid @RequestBody FindIdRequest request) {
        return ApiResponse.success(authService.findId(request));
    }

    @PostMapping("/verify-reset")
    @Operation(summary = "Verify password reset", description = "Verify identity and issue a password reset token.")
    public ApiResponse<VerifyResetResponse> verifyReset(@Valid @RequestBody VerifyResetRequest request) {
        return ApiResponse.success(authService.verifyReset(request));
    }

    @PatchMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Change password with a reset token.")
    public ApiResponse<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(authService.resetPassword(request));
    }
}
