package com.mock.maesoongan.authservice.onprem;

public record OnPremPasswordChangeRequest(
        String requestId,
        Long memberId,
        String currentPassword,
        String newPassword
) {
}
