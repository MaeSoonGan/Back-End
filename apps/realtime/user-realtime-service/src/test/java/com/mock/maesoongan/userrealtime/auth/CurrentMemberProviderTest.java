package com.mock.maesoongan.userrealtime.auth;

import com.mock.maesoongan.userrealtime.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentMemberProviderTest {

    private final CurrentMemberProvider currentMemberProvider = new CurrentMemberProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void memberIdReturnsAuthenticatedCurrentMemberId() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentMember(7L, "user01"),
                null
        ));

        Long memberId = currentMemberProvider.memberId();

        assertThat(memberId).isEqualTo(7L);
    }

    @Test
    void memberIdThrowsUnauthorizedWhenPrincipalIsMissing() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(currentMemberProvider::memberId)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("UNAUTHORIZED");
                });
    }
}
