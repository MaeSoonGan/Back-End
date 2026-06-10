package com.mock.maesoongan.adminservice.auth;

public final class AdminAuthDtos {

    private AdminAuthDtos() {
    }

    public record AdminLoginRequest(
            String loginId,
            String password
    ) {
    }

    public record AdminLoginResponse(
            String token,
            String loginId,
            String nickname,
            String role
    ) {
    }
}
