package com.mock.maesoongan.adminservice;

import com.mock.maesoongan.adminservice.auth.AdminAuthDtos;
import com.mock.maesoongan.adminservice.auth.AdminAuthService;
import com.mock.maesoongan.adminservice.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        adminAuthService = new AdminAuthService(jdbcTemplate);
    }

    @Test
    void loginReturnsAdminTokenWhenCredentialsAreValid() throws Exception {
        mockAdminRow("admin", "{noop}secret", "Admin", "SUPER_ADMIN", "ACTIVE");

        AdminAuthDtos.AdminLoginResponse response = adminAuthService.login(new AdminAuthDtos.AdminLoginRequest(
                "admin",
                "secret"
        ));

        assertThat(response.token()).isEqualTo("admin-token");
        assertThat(response.loginId()).isEqualTo("admin");
        assertThat(response.nickname()).isEqualTo("Admin");
        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void loginThrowsBadRequestWhenCredentialsAreMissing() {
        assertThatThrownBy(() -> adminAuthService.login(new AdminAuthDtos.AdminLoginRequest("", "")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("BAD_REQUEST");
                });
    }

    @Test
    void loginThrowsUnauthorizedWhenAdminDoesNotExist() {
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate)
                .queryForObject(anyString(), any(RowMapper.class), any(Object[].class));

        assertThatThrownBy(() -> adminAuthService.login(new AdminAuthDtos.AdminLoginRequest("missing", "secret")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("ADMIN_LOGIN_FAILED");
                });
    }

    @Test
    void loginThrowsUnauthorizedWhenPasswordDoesNotMatch() throws Exception {
        mockAdminRow("admin", "{noop}secret", "Admin", "SUPER_ADMIN", "ACTIVE");

        assertThatThrownBy(() -> adminAuthService.login(new AdminAuthDtos.AdminLoginRequest("admin", "wrong")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("ADMIN_LOGIN_FAILED");
                });
    }

    @Test
    void loginThrowsForbiddenWhenAdminIsInactive() throws Exception {
        mockAdminRow("admin", "{noop}secret", "Admin", "SUPER_ADMIN", "INACTIVE");

        assertThatThrownBy(() -> adminAuthService.login(new AdminAuthDtos.AdminLoginRequest("admin", "secret")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.code()).isEqualTo("ADMIN_INACTIVE");
                });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockAdminRow(
            String loginId,
            String password,
            String nickname,
            String role,
            String status
    ) throws Exception {
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("login_id")).thenReturn(loginId);
            when(resultSet.getString("password")).thenReturn(password);
            when(resultSet.getString("nickname")).thenReturn(nickname);
            when(resultSet.getString("role")).thenReturn(role);
            when(resultSet.getString("status")).thenReturn(status);
            return mapper.mapRow(resultSet, 0);
        }).when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }
}
