package com.mock.maesoongan.authservice.onprem;

public record OnPremMemberDeleteRequest(
        String requestId,
        Long memberId,
        String password
) {
}
