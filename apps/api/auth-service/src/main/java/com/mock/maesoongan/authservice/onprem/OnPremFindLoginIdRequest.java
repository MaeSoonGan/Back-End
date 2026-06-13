package com.mock.maesoongan.authservice.onprem;

public record OnPremFindLoginIdRequest(
        String requestId,
        String email,
        String phone
) {
}
