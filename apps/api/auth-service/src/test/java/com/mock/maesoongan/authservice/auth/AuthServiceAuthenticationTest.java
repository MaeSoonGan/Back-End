package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.LoginRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ReissueRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.TokenResponse;
import com.mock.maesoongan.authservice.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceAuthenticationTest {

    private JdbcTemplate jdbcTemplate;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenStore refreshTokenStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = new JwtTokenProvider("test-secret");
        refreshTokenStore = new RefreshTokenStore();
        authService = new AuthService(
                jdbcTemplate,
                null,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenStore,
                new VerificationCodeStore(),
                null,
                null
        );
    }

    @Test
    void loginIssuesTokensAndStoresRefreshToken() throws Exception {
        mockAuthMember("ACTIVE", 0, "{noop}Password1!", null);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        TokenResponse response = authService.login(new LoginRequest("testuser", "Password1!", false));

        assertThat(jwtTokenProvider.validateAccessToken(response.accessToken())).isEqualTo("testuser");
        assertThat(jwtTokenProvider.validateRefreshToken(response.refreshToken())).isEqualTo("testuser");
        assertThat(refreshTokenStore.isActive("testuser", response.refreshToken())).isTrue();
    }

    @Test
    void loginThrowsForbiddenWhenMemberIsWithdrawn() throws Exception {
        mockAuthMember("DELETED", 0, "{noop}Password1!", null);

        assertThatThrownBy(() -> authService.login(new LoginRequest("testuser", "Password1!", false)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void reissueRotatesRefreshToken() {
        String oldRefreshToken = jwtTokenProvider.createRefreshToken("testuser", false);
        refreshTokenStore.save("testuser", oldRefreshToken);

        TokenResponse response = authService.reissue(new ReissueRequest(oldRefreshToken));

        assertThat(jwtTokenProvider.validateAccessToken(response.accessToken())).isEqualTo("testuser");
        assertThat(jwtTokenProvider.validateRefreshToken(response.refreshToken())).isEqualTo("testuser");
        assertThat(refreshTokenStore.isActive("testuser", oldRefreshToken)).isFalse();
        assertThat(refreshTokenStore.isActive("testuser", response.refreshToken())).isTrue();
    }

    @Test
    void reissueRejectsInactiveRefreshToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken("testuser", false);

        assertThatThrownBy(() -> authService.reissue(new ReissueRequest(refreshToken)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockAuthMember(String status, int failCount, String passwordHash, LocalDateTime lockedUntil) throws Exception {
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("member_id")).thenReturn(7L);
            when(resultSet.getString("login_id")).thenReturn("testuser");
            when(resultSet.getString("email")).thenReturn("test@example.com");
            when(resultSet.getString("nickname")).thenReturn("tester");
            when(resultSet.getString("status")).thenReturn(status);
            when(resultSet.getInt("login_fail_count")).thenReturn(failCount);
            when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 10, 10, 0)));
            when(resultSet.getString("password_hash")).thenReturn(passwordHash);
            when(resultSet.getTimestamp("locked_until")).thenReturn(lockedUntil == null ? null : Timestamp.valueOf(lockedUntil));
            return mapper.mapRow(resultSet, 0);
        }).when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }
}
