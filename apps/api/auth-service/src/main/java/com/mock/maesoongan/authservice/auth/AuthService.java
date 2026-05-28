package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.AvailabilityResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ExpiresInResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.FindIdRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.FindIdResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.LoginRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ReissueRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.RegisterRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.RegisterResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ResetPasswordRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ResetPasswordResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.SendEmailCodeRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.TokenResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifiedResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyCodeRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyResetRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyResetResponse;
import com.mock.maesoongan.authservice.common.BusinessException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String EMAIL = "email";
    private static final String SIGNUP = "signup";
    private static final String FIND_ID = "find-id";
    private static final String RESET_PASSWORD = "reset-password";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final VerificationCodeStore verificationCodeStore;
    private final EmailCodeSender emailCodeSender;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AuthMember member = findByLoginId(request.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid userId or password."));

        if (member.isLocked()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account is locked.");
        }

        if (!passwordMatches(request.password(), member.passwordHash())) {
            increaseLoginFailCount(member.memberId(), member.loginFailCount());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid userId or password.");
        }

        resetLoginFailCount(member.memberId());
        String accessToken = jwtTokenProvider.createAccessToken(member.loginId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.loginId(), request.keepLoginValue());
        refreshTokenStore.save(member.loginId(), refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse reissue(ReissueRequest request) {
        String userId = jwtTokenProvider.validateRefreshToken(request.refreshToken());
        if (!refreshTokenStore.isActive(userId, request.refreshToken())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, false);
        refreshTokenStore.replace(userId, request.refreshToken(), refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkId(String userId) {
        validateUserId(userId);
        return new AvailabilityResponse(!exists("select count(*) from member_snapshot where login_id = ?", userId));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkNickname(String nickname) {
        if (nickname == null || nickname.length() < 2 || nickname.length() > 10) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Nickname must be 2 to 10 characters.");
        }
        return new AvailabilityResponse(!exists("select count(*) from member_snapshot where nickname = ?", nickname));
    }

    @Transactional(readOnly = true)
    public ExpiresInResponse sendEmailCode(SendEmailCodeRequest request) {
        if (SIGNUP.equals(request.purpose()) && exists("select count(*) from member_snapshot where email = ?", request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        if ((FIND_ID.equals(request.purpose()) || RESET_PASSWORD.equals(request.purpose()))
                && !exists("select count(*) from member_snapshot where email = ?", request.email())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Email is not registered.");
        }

        String code = verificationCodeStore.issue(EMAIL, request.email(), request.purpose());
        emailCodeSender.send(request.email(), code);
        return new ExpiresInResponse(verificationCodeStore.expiresInSeconds());
    }

    public VerifiedResponse verifyEmailCode(VerifyCodeRequest request) {
        String purpose = verificationCodeStore.verifyAny(
                EMAIL,
                request.email(),
                request.code(),
                SIGNUP,
                FIND_ID,
                RESET_PASSWORD
        );
        if (SIGNUP.equals(purpose)) {
            verificationCodeStore.markSignupEmailVerified(request.email());
        }
        return new VerifiedResponse(true);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        validateUserId(request.userId());
        if (!verificationCodeStore.isSignupEmailVerified(request.email())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Email verification is required.");
        }
        if (exists("select count(*) from member_snapshot where login_id = ?", request.userId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "UserId is already in use.");
        }
        if (exists("select count(*) from member_snapshot where email = ?", request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        if (exists("select count(*) from member_snapshot where nickname = ?", request.nickname())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Nickname is already in use.");
        }

        long memberId = nextMemberId();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        insert into member_snapshot
                        (member_id, login_id, email, nickname, phone, status, login_fail_count, email_verified,
                         profile_image_url, created_at, updated_at, synced_at)
                        values (?, ?, ?, ?, ?, 'ACTIVE', 0, true, null, ?, null, ?)
                        """,
                memberId,
                request.userId(),
                request.email(),
                request.nickname(),
                request.phone(),
                now,
                now);
        jdbcTemplate.update("""
                        insert into dev_member_auth
                        (member_id, password_hash, password_updated_at, login_fail_count, locked_until, created_at, updated_at)
                        values (?, ?, ?, 0, null, ?, null)
                        """,
                memberId,
                passwordEncoder.encode(request.password()),
                now,
                now);

        return new RegisterResponse(request.userId(), request.nickname(), request.email());
    }

    @Transactional(readOnly = true)
    public FindIdResponse findId(FindIdRequest request) {
        AuthMember member = findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Email is not registered."));
        verificationCodeStore.verify(EMAIL, request.email(), FIND_ID, request.code());

        return new FindIdResponse(
                maskUserId(member.loginId()),
                maskEmail(member.email()),
                member.createdAt().format(DATE_FORMAT)
        );
    }

    @Transactional(readOnly = true)
    public VerifyResetResponse verifyReset(VerifyResetRequest request) {
        verificationCodeStore.verify(EMAIL, request.email(), RESET_PASSWORD, request.code());
        AuthMember member = findByLoginIdAndNicknameAndEmail(request.userId(), request.name(), request.email())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "No matching user found."));

        return new VerifyResetResponse(
                jwtTokenProvider.createResetToken(member.loginId()),
                maskUserId(member.loginId())
        );
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "New password confirmation does not match.");
        }

        String userId = jwtTokenProvider.validateResetToken(request.resetToken());
        AuthMember member = findByLoginId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid reset token."));

        if (passwordMatches(request.newPassword(), member.passwordHash())) {
            throw new BusinessException(HttpStatus.CONFLICT, "New password must be different from the old password.");
        }

        LocalDateTime changedAt = LocalDateTime.now();
        jdbcTemplate.update("""
                        update dev_member_auth
                        set password_hash = ?, password_updated_at = ?, login_fail_count = 0, locked_until = null, updated_at = ?
                        where member_id = ?
                        """,
                passwordEncoder.encode(request.newPassword()),
                changedAt,
                changedAt,
                member.memberId());
        jdbcTemplate.update("""
                        update member_snapshot
                        set login_fail_count = 0, status = 'ACTIVE', updated_at = ?, synced_at = ?
                        where member_id = ?
                        """,
                changedAt,
                changedAt,
                member.memberId());
        refreshTokenStore.revokeAll(member.loginId());

        return new ResetPasswordResponse(maskUserId(member.loginId()), changedAt.format(DATE_TIME_FORMAT));
    }

    private Optional<AuthMember> findByLoginId(String loginId) {
        return findOne("""
                select ms.member_id, ms.login_id, ms.email, ms.nickname, ms.status, ms.login_fail_count,
                       ms.created_at, dma.password_hash, dma.locked_until
                from member_snapshot ms
                join dev_member_auth dma on dma.member_id = ms.member_id
                where ms.login_id = ?
                """, loginId);
    }

    private Optional<AuthMember> findByEmail(String email) {
        return findOne("""
                select ms.member_id, ms.login_id, ms.email, ms.nickname, ms.status, ms.login_fail_count,
                       ms.created_at, dma.password_hash, dma.locked_until
                from member_snapshot ms
                join dev_member_auth dma on dma.member_id = ms.member_id
                where ms.email = ?
                """, email);
    }

    private Optional<AuthMember> findByLoginIdAndNicknameAndEmail(String loginId, String nickname, String email) {
        return findOne("""
                select ms.member_id, ms.login_id, ms.email, ms.nickname, ms.status, ms.login_fail_count,
                       ms.created_at, dma.password_hash, dma.locked_until
                from member_snapshot ms
                join dev_member_auth dma on dma.member_id = ms.member_id
                where ms.login_id = ? and ms.nickname = ? and ms.email = ?
                """, loginId, nickname, email);
    }

    private Optional<AuthMember> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new AuthMember(
                    rs.getLong("member_id"),
                    rs.getString("login_id"),
                    rs.getString("email"),
                    rs.getString("nickname"),
                    rs.getString("status"),
                    rs.getInt("login_fail_count"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    rs.getString("password_hash"),
                    toLocalDateTime(rs.getTimestamp("locked_until"))
            ), args));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (storedPassword != null && storedPassword.startsWith("{noop}")) {
            return rawPassword.equals(storedPassword.substring("{noop}".length()));
        }
        return storedPassword != null && passwordEncoder.matches(rawPassword, storedPassword);
    }

    private void increaseLoginFailCount(long memberId, int currentFailCount) {
        int nextFailCount = currentFailCount + 1;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = nextFailCount >= 5 ? now.plusMinutes(30) : null;
        String status = nextFailCount >= 5 ? "SUSPENDED" : "ACTIVE";

        jdbcTemplate.update("""
                        update dev_member_auth
                        set login_fail_count = ?, locked_until = ?, updated_at = ?
                        where member_id = ?
                        """,
                nextFailCount,
                lockedUntil,
                now,
                memberId);
        jdbcTemplate.update("""
                        update member_snapshot
                        set login_fail_count = ?, status = ?, updated_at = ?, synced_at = ?
                        where member_id = ?
                        """,
                nextFailCount,
                status,
                now,
                now,
                memberId);
    }

    private void resetLoginFailCount(long memberId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        update dev_member_auth
                        set login_fail_count = 0, locked_until = null, updated_at = ?
                        where member_id = ?
                        """, now, memberId);
        jdbcTemplate.update("""
                        update member_snapshot
                        set login_fail_count = 0, updated_at = ?, synced_at = ?
                        where member_id = ?
                        """, now, now, memberId);
    }

    private boolean exists(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
    }

    private long nextMemberId() {
        Long value = jdbcTemplate.queryForObject("select coalesce(max(member_id), 0) + 1 from member_snapshot", Long.class);
        return value == null ? 1 : value;
    }

    private void validateUserId(String userId) {
        if (userId == null || !userId.matches("^[A-Za-z0-9]{5,20}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "UserId must be 5 to 20 alphanumeric characters.");
        }
    }

    private String maskUserId(String userId) {
        if (userId.length() <= 4) {
            return "*".repeat(userId.length());
        }
        return userId.substring(0, userId.length() - 4) + "****";
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "*" + email.substring(atIndex);
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        int visibleLength = Math.min(4, localPart.length() - 1);
        return localPart.substring(0, visibleLength) + "***" + domain;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record AuthMember(
            long memberId,
            String loginId,
            String email,
            String nickname,
            String status,
            int loginFailCount,
            LocalDateTime createdAt,
            String passwordHash,
            LocalDateTime lockedUntil
    ) {
        boolean isLocked() {
            return "SUSPENDED".equals(status) || (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()));
        }
    }
}
