package com.mock.maesoongan.authservice.onprem;

public record OnPremLoginRequest(
        String requestId,
        String loginId,
        String email,
        String password
) {
}
