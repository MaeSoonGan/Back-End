package com.mock.maesoongan.authservice.onprem;

public record OnPremMemberUpdateRequest(
        String requestId,
        Long memberId,
        String nickname,
        String phone
) {
}
