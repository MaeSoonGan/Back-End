package com.mock.maesoongan.authservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mock.maesoongan.authservice.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider("test-secret");

    @Test
    void validateRefreshTokenReturnsSubject() {
        String refreshToken = jwtTokenProvider.createRefreshToken("testuser", false);

        String subject = jwtTokenProvider.validateRefreshToken(refreshToken);

        assertThat(subject).isEqualTo("testuser");
    }

    @Test
    void validateRefreshTokenRejectsResetToken() {
        String resetToken = jwtTokenProvider.createResetToken("testuser").substring(4);

        assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(resetToken))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
