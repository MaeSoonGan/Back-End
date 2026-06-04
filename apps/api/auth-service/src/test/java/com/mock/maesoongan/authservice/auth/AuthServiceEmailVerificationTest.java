package com.mock.maesoongan.authservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyCodeRequest;
import org.junit.jupiter.api.Test;

class AuthServiceEmailVerificationTest {

    @Test
    void verifySignupCodeStoresVerifiedEmailInServerCache() {
        VerificationCodeStore verificationCodeStore = new VerificationCodeStore();
        String code = verificationCodeStore.issue("email", "signup@example.com", "signup");
        AuthService authService = new AuthService(
                null,
                null,
                null,
                null,
                null,
                verificationCodeStore,
                null
        );

        authService.verifyEmailCode(new VerifyCodeRequest("signup@example.com", code));

        assertThat(verificationCodeStore.isSignupEmailVerified("signup@example.com")).isTrue();
    }
}
