package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.MemberProfileResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.UpdateMemberProfileRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.WithdrawMemberRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.WithdrawMemberResponse;
import com.mock.maesoongan.authservice.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Member Profile", description = "My profile management APIs")
public class MemberProfileController {

    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Get my profile")
    public ApiResponse<MemberProfileResponse> getMyProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return ApiResponse.success(authService.getMyProfile(authorization));
    }

    @PatchMapping
    @Operation(summary = "Update my profile")
    public ApiResponse<MemberProfileResponse> updateMyProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody UpdateMemberProfileRequest request
    ) {
        return ApiResponse.success(authService.updateMyProfile(authorization, request));
    }

    @PatchMapping("/password")
    @Operation(summary = "Change my password")
    public ApiResponse<ChangePasswordResponse> changeMyPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return ApiResponse.success(authService.changeMyPassword(authorization, request));
    }

    @DeleteMapping
    @Operation(summary = "Withdraw my account")
    public ApiResponse<WithdrawMemberResponse> withdrawMe(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody WithdrawMemberRequest request
    ) {
        return ApiResponse.success(authService.withdrawMe(authorization, request));
    }
}
