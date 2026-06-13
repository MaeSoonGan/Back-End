package com.mock.maesoongan.authservice.onprem;

public record OnPremPasswordResetRequest(
        String requestId,
        String email,
        String newPassword
) {
}
