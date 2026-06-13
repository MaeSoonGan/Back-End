package com.mock.maesoongan.authservice.onprem;

public record OnPremApiResponse<T>(
        boolean success,
        T data,
        OnPremError error
) {
}
