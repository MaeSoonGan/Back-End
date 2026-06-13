package com.mock.maesoongan.authservice.onprem;

import com.mock.maesoongan.authservice.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OnPremMemberClient {

    private final RestClient restClient;
    private final String signupPath;
    private final String loginPath;
    private final String loginIdFindPath;
    private final String passwordChangePath;
    private final String passwordResetPath;
    private final String memberDeletePath;
    private final String memberUpdatePath;
    private final String serviceAuthToken;

    public OnPremMemberClient(
            @Value("${app.onprem.member-service.base-url:http://localhost:8083}") String baseUrl,
            @Value("${app.onprem.member-service.signup-path:/api/members/signup}") String signupPath,
            @Value("${app.onprem.member-service.login-path:/api/members/login/verify}") String loginPath,
            @Value("${app.onprem.member-service.login-id-find-path:/api/members/login-id/find}") String loginIdFindPath,
            @Value("${app.onprem.member-service.password-change-path:/api/members/password/change}") String passwordChangePath,
            @Value("${app.onprem.member-service.password-reset-path:/api/members/password/reset}") String passwordResetPath,
            @Value("${app.onprem.member-service.delete-path:/api/members/delete}") String memberDeletePath,
            @Value("${app.onprem.member-service.update-path:/api/members/update}") String memberUpdatePath,
            @Value("${app.onprem.member-service.auth-token:test-token}") String serviceAuthToken
    ) {
        this.restClient = RestClient.builder().baseUrl(trimTrailingSlash(baseUrl)).build();
        this.signupPath = normalizePath(signupPath, "/api/members/signup");
        this.loginPath = normalizePath(loginPath, "/api/members/login/verify");
        this.loginIdFindPath = normalizePath(loginIdFindPath, "/api/members/login-id/find");
        this.passwordChangePath = normalizePath(passwordChangePath, "/api/members/password/change");
        this.passwordResetPath = normalizePath(passwordResetPath, "/api/members/password/reset");
        this.memberDeletePath = normalizePath(memberDeletePath, "/api/members/delete");
        this.memberUpdatePath = normalizePath(memberUpdatePath, "/api/members/update");
        this.serviceAuthToken = serviceAuthToken;
    }

    public OnPremCommandData signup(OnPremSignupRequest request) {
        return command(signupPath, request, "Failed to request on-premise signup.");
    }

    public OnPremCommandData login(OnPremLoginRequest request) {
        return command(loginPath, request, "Failed to request on-premise login.");
    }

    public OnPremCommandData findLoginId(OnPremFindLoginIdRequest request) {
        return command(loginIdFindPath, request, "Failed to request on-premise loginId find.");
    }

    public OnPremCommandData changePassword(OnPremPasswordChangeRequest request) {
        return command(passwordChangePath, request, "Failed to request on-premise password change.");
    }

    public OnPremCommandData resetPassword(OnPremPasswordResetRequest request) {
        return command(passwordResetPath, request, "Failed to request on-premise password reset.");
    }

    public OnPremCommandData deleteMember(OnPremMemberDeleteRequest request) {
        return command(memberDeletePath, request, "Failed to request on-premise member delete.");
    }

    public OnPremCommandData updateMember(OnPremMemberUpdateRequest request) {
        return command(memberUpdatePath, request, "Failed to request on-premise member update.");
    }

    private OnPremCommandData command(String path, Object request, String failureMessage) {
        try {
            OnPremApiResponse<OnPremCommandData> response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAuthToken)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, failureMessage);
            }
            if (!response.success()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, errorMessage(response.error(), failureMessage));
            }
            OnPremCommandData data = response.data();
            if (data == null) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, failureMessage);
            }
            if (!"SUCCESS".equalsIgnoreCase(data.status())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, data.reason() == null ? failureMessage : data.reason());
            }
            return data;
        } catch (RestClientResponseException exception) {
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            throw new BusinessException(
                    status == null ? HttpStatus.BAD_GATEWAY : status,
                    responseMessage(exception)
            );
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, failureMessage);
        }
    }

    private String errorMessage(OnPremError error, String fallback) {
        if (error == null) {
            return fallback;
        }
        if (StringUtils.hasText(error.message())) {
            return error.message();
        }
        return StringUtils.hasText(error.code()) ? error.code() : fallback;
    }

    private String responseMessage(RestClientResponseException exception) {
        if (StringUtils.hasText(exception.getResponseBodyAsString())) {
            return exception.getResponseBodyAsString();
        }
        return "On-premise signup request failed.";
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value) || value.length() == 1) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizePath(String value, String defaultPath) {
        if (!StringUtils.hasText(value)) {
            return defaultPath;
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
