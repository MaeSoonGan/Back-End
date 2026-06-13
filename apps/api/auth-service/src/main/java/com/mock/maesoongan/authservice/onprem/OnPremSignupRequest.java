package com.mock.maesoongan.authservice.onprem;

public record OnPremSignupRequest(
        String requestId,
        String email,
        String loginId,
        String password,
        String nickname,
        String phone
) {
}
