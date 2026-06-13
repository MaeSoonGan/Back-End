package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.MemberProfileResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ProfileImageUploadUrlRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ProfileImageUploadUrlResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.UpdateMemberProfileRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.WithdrawMemberRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.WithdrawMemberResponse;
import com.mock.maesoongan.authservice.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me")
@Tag(name = "Member Profile", description = "My profile management APIs")
public class MemberProfileController {

    private static final String SWAGGER_AUTHORIZATION_HEADER = "X-Authorization";

    private final AuthService authService;
    private final ProfileImageService profileImageService;

    @GetMapping
    @Operation(summary = "Get my profile")
    public ApiResponse<MemberProfileResponse> getMyProfile(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = SWAGGER_AUTHORIZATION_HEADER, required = false) String swaggerAuthorization
    ) {
        return ApiResponse.success(authService.getMyProfile(resolveAuthorization(authorization, swaggerAuthorization)));
    }

    @PatchMapping
    @Operation(summary = "Update my profile")
    public ApiResponse<MemberProfileResponse> updateMyProfile(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = SWAGGER_AUTHORIZATION_HEADER, required = false) String swaggerAuthorization,
            @Valid @RequestBody UpdateMemberProfileRequest request
    ) {
        return ApiResponse.success(authService.updateMyProfile(resolveAuthorization(authorization, swaggerAuthorization), request));
    }

    @PatchMapping("/password")
    @Operation(summary = "Change my password")
    public ApiResponse<ChangePasswordResponse> changeMyPassword(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = SWAGGER_AUTHORIZATION_HEADER, required = false) String swaggerAuthorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return ApiResponse.success(authService.changeMyPassword(resolveAuthorization(authorization, swaggerAuthorization), request));
    }

    @DeleteMapping
    @Operation(summary = "Withdraw my account")
    public ApiResponse<WithdrawMemberResponse> withdrawMe(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = SWAGGER_AUTHORIZATION_HEADER, required = false) String swaggerAuthorization,
            @Valid @RequestBody WithdrawMemberRequest request
    ) {
        return ApiResponse.success(authService.withdrawMe(resolveAuthorization(authorization, swaggerAuthorization), request));
    }

    @PostMapping("/profile-image/presigned-url")
    @Operation(summary = "Get presigned URL for profile image upload")
    public ApiResponse<ProfileImageUploadUrlResponse> getProfileImageUploadUrl(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = SWAGGER_AUTHORIZATION_HEADER, required = false) String swaggerAuthorization,
            @Valid @RequestBody ProfileImageUploadUrlRequest request
    ) {
        long memberId = authService.currentMemberId(resolveAuthorization(authorization, swaggerAuthorization));
        return ApiResponse.success(profileImageService.createUploadUrl(memberId, request.contentType()));
    }

    private String resolveAuthorization(String authorization, String swaggerAuthorization) {
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }
        return swaggerAuthorization;
    }
}
