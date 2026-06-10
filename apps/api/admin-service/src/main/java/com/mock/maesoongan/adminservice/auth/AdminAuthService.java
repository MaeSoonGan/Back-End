package com.mock.maesoongan.adminservice.auth;

import com.mock.maesoongan.adminservice.auth.AdminAuthDtos.AdminLoginRequest;
import com.mock.maesoongan.adminservice.auth.AdminAuthDtos.AdminLoginResponse;
import com.mock.maesoongan.adminservice.common.BusinessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAuthService {

    // admin-service 인증은 현재 dev 고정 토큰(Bearer admin-token)을 사용하므로 로그인 성공 시 동일 토큰을 발급한다.
    private static final String ADMIN_TOKEN = "admin-token";

    private final JdbcTemplate jdbcTemplate;
    // 저장된 비밀번호가 {noop}/{bcrypt} 등 prefix 형식이라 DelegatingPasswordEncoder로 검증
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public AdminAuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminLoginResponse login(AdminLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.loginId()) || !StringUtils.hasText(request.password())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "아이디와 비밀번호를 입력해주세요.");
        }

        AdminRow admin;
        try {
            admin = jdbcTemplate.queryForObject("""
                    select login_id, password, coalesce(nickname, login_id) as nickname, role, status
                    from admin
                    where login_id = ?
                    """, (rs, rowNum) -> new AdminRow(
                    rs.getString("login_id"),
                    rs.getString("password"),
                    rs.getString("nickname"),
                    rs.getString("role"),
                    rs.getString("status")
            ), request.loginId());
        } catch (EmptyResultDataAccessException exception) {
            throw loginFailed();
        }

        if (admin.status() != null && !"ACTIVE".equalsIgnoreCase(admin.status())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_INACTIVE", "비활성화된 관리자 계정입니다.");
        }
        if (!passwordEncoder.matches(request.password(), admin.password())) {
            throw loginFailed();
        }

        return new AdminLoginResponse(ADMIN_TOKEN, admin.loginId(), admin.nickname(), admin.role());
    }

    private BusinessException loginFailed() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_FAILED", "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    private record AdminRow(String loginId, String password, String nickname, String role, String status) {
    }
}
