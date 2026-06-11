package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.MemberProfileResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.UpdateMemberProfileRequest;
import com.mock.maesoongan.authservice.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceProfileTest {

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
    void getMyProfileReturnsActiveMemberProfile() throws Exception {
        mockMemberProfile();
        String authorization = authorizationHeader();

        MemberProfileResponse response = authService.getMyProfile(authorization);

        assertThat(response.userId()).isEqualTo("testuser");
        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.phone()).isEqualTo("010-1234-5678");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.emailVerified()).isTrue();
    }

    @Test
    void currentMemberIdThrowsUnauthorizedWhenAuthorizationHeaderIsMissing() {
        assertThatThrownBy(() -> authService.currentMemberId(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void updateMyProfileAppliesNullableFields() throws Exception {
        mockMemberProfile();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        MemberProfileResponse response = authService.updateMyProfile(
                authorizationHeader(),
                new UpdateMemberProfileRequest(
                        "tester",
                        "010-2222-3333",
                        null,
                        null,
                        "https://cdn.example.com/profile.png"
                )
        );

        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.phone()).isEqualTo("010-2222-3333");
        assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example.com/profile.png");
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void updateMyProfileThrowsBadRequestWhenEmailCodeIsMissingForNewEmail() throws Exception {
        mockMemberProfile();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        assertThatThrownBy(() -> authService.updateMyProfile(
                authorizationHeader(),
                new UpdateMemberProfileRequest(null, null, "new@example.com", null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void changeMyPasswordThrowsBadRequestWhenConfirmationDoesNotMatch() {
        assertThatThrownBy(() -> authService.changeMyPassword(
                authorizationHeader(),
                new ChangePasswordRequest("Password1!", "NewPassword1!", "Mismatch1!")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockMemberProfile() throws Exception {
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("member_id")).thenReturn(7L);
            when(resultSet.getString("login_id")).thenReturn("testuser");
            when(resultSet.getString("email")).thenReturn("test@example.com");
            when(resultSet.getString("nickname")).thenReturn("tester");
            when(resultSet.getString("phone")).thenReturn("010-1234-5678");
            when(resultSet.getBoolean("email_verified")).thenReturn(true);
            when(resultSet.getString("profile_image_url")).thenReturn(null);
            when(resultSet.getString("password_hash")).thenReturn("{noop}Password1!");
            return mapper.mapRow(resultSet, 0);
        }).when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }

    private String authorizationHeader() {
        return "Bearer " + jwtTokenProvider.createAccessToken("testuser");
    }
}
