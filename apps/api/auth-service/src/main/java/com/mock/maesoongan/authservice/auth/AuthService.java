package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.AvailabilityResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ExpiresInResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.FindIdRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.FindIdResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.LoginRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.MemberProfileResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ReissueRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.RegisterRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.RegisterResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ResetPasswordRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.ResetPasswordResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.SendEmailCodeRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.TokenResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.UpdateMemberProfileRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifiedResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyCodeRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyResetRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyResetResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.WithdrawMemberRequest;
import com.mock.maesoongan.authservice.auth.AuthDtos.WithdrawMemberResponse;
import com.mock.maesoongan.authservice.common.BusinessException;
import com.mock.maesoongan.authservice.onprem.OnPremCommandData;
import com.mock.maesoongan.authservice.onprem.OnPremFindLoginIdRequest;
import com.mock.maesoongan.authservice.onprem.OnPremLoginRequest;
import com.mock.maesoongan.authservice.onprem.OnPremMemberDeleteRequest;
import com.mock.maesoongan.authservice.onprem.OnPremMemberClient;
import com.mock.maesoongan.authservice.onprem.OnPremMemberUpdateRequest;
import com.mock.maesoongan.authservice.onprem.OnPremPasswordChangeRequest;
import com.mock.maesoongan.authservice.onprem.OnPremPasswordResetRequest;
import com.mock.maesoongan.authservice.onprem.OnPremSignupRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final VerificationCodeStore verificationCodeStore;
    private final EmailCodeSender emailCodeSender;
    private final OnPremMemberClient onPremMemberClient;
    private final SignupRequestIdStore signupRequestIdStore;

    public TokenResponse login(LoginRequest request) {
        onPremMemberClient.login(new OnPremLoginRequest(
                UUID.randomUUID().toString(),
                request.userId(),
                null,
                request.password()
        ));
        String accessToken = jwtTokenProvider.createAccessToken(request.userId());
        String refreshToken = jwtTokenProvider.createRefreshToken(request.userId(), request.keepLoginValue());
        refreshTokenStore.save(request.userId(), refreshToken);
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
                && !exists("select count(*) from member_snapshot where email = ? and status <> 'DELETED'", request.email())) {
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

        String requestId = signupRequestIdStore.getOrIssue(request.email(), request.userId());
        onPremMemberClient.signup(new OnPremSignupRequest(
                requestId,
                request.email(),
                request.userId(),
                request.password(),
                request.nickname(),
                request.phone()
        ));

        return new RegisterResponse(requestId, request.userId(), request.nickname(), request.email());
    }

    @Transactional(readOnly = true)
    public FindIdResponse findId(FindIdRequest request) {
        verificationCodeStore.verify(EMAIL, request.email(), FIND_ID, request.code());
        onPremMemberClient.findLoginId(new OnPremFindLoginIdRequest(
                UUID.randomUUID().toString(),
                request.email(),
                request.phone()
        ));
        AuthMember member = findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(HttpStatus.ACCEPTED, "LoginId find request accepted."));

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
        OnPremCommandData result = onPremMemberClient.resetPassword(new OnPremPasswordResetRequest(
                UUID.randomUUID().toString(),
                member.email(),
                request.newPassword()
        ));
        refreshTokenStore.revokeAll(member.loginId());

        return new ResetPasswordResponse(maskUserId(member.loginId()), formatProcessedAt(result));
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getMyProfile(String authorizationHeader) {
        return toProfileResponse(findActiveProfileByLoginId(currentUserId(authorizationHeader)));
    }

    @Transactional(readOnly = true)
    public long currentMemberId(String authorizationHeader) {
        return findActiveProfileByLoginId(currentUserId(authorizationHeader)).memberId();
    }

    @Transactional
    public MemberProfileResponse updateMyProfile(String authorizationHeader, UpdateMemberProfileRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }

        MemberProfile profile = findActiveProfileByLoginId(currentUserId(authorizationHeader));
        String nextNickname = resolveNickname(profile, request.nickname());
        String nextPhone = resolvePhone(profile, request.phone());
        String nextEmail = resolveEmail(profile, request.email());
        String nextProfileImageUrl = resolveProfileImageUrl(profile, request.profileImageUrl());
        if ((request.nickname() != null && !request.nickname().isBlank()) || (request.phone() != null && !request.phone().isBlank())) {
            onPremMemberClient.updateMember(new OnPremMemberUpdateRequest(
                    UUID.randomUUID().toString(),
                    profile.memberId(),
                    request.nickname(),
                    request.phone()
            ));
        }
        boolean emailChanged = !profile.email().equals(nextEmail);
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update("""
                        update member_snapshot
                        set nickname = ?,
                            phone = ?,
                            email = ?,
                            email_verified = ?,
                            profile_image_url = ?,
                            updated_at = ?,
                            synced_at = ?
                        where member_id = ? and status = 'ACTIVE'
                        """,
                nextNickname,
                nextPhone,
                nextEmail,
                emailChanged ? true : profile.emailVerified(),
                nextProfileImageUrl,
                now,
                now,
                profile.memberId());

        return new MemberProfileResponse(
                profile.loginId(),
                nextNickname,
                nextPhone,
                nextEmail,
                emailChanged ? true : profile.emailVerified(),
                nextProfileImageUrl
        );
    }

    @Transactional
    public ChangePasswordResponse changeMyPassword(String authorizationHeader, ChangePasswordRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "New password confirmation does not match.");
        }

        MemberProfile profile = findActiveProfileByLoginId(currentUserId(authorizationHeader));
        OnPremCommandData result = onPremMemberClient.changePassword(new OnPremPasswordChangeRequest(
                UUID.randomUUID().toString(),
                profile.memberId(),
                request.currentPassword(),
                request.newPassword()
        ));
        refreshTokenStore.revokeAll(profile.loginId());

        return new ChangePasswordResponse(formatProcessedAt(result));
    }

    @Transactional
    public WithdrawMemberResponse withdrawMe(String authorizationHeader, WithdrawMemberRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }

        MemberProfile profile = findActiveProfileByLoginId(currentUserId(authorizationHeader));
        OnPremCommandData result = onPremMemberClient.deleteMember(new OnPremMemberDeleteRequest(
                UUID.randomUUID().toString(),
                profile.memberId(),
                request.password()
        ));
        refreshTokenStore.revokeAll(profile.loginId());

        return new WithdrawMemberResponse(formatProcessedAt(result));
    }

    private Optional<AuthMember> findByLoginId(String loginId) {
        return findOne("""
                select ms.member_id, ms.login_id, ms.email, ms.nickname, ms.status, ms.login_fail_count,
                       ms.created_at
                from member_snapshot ms
                where ms.login_id = ?
                """, loginId);
    }

    private Optional<AuthMember> findByEmail(String email) {
        return findOne("""
                select ms.member_id, ms.login_id, ms.email, ms.nickname, ms.status, ms.login_fail_count,
                       ms.created_at
                from member_snapshot ms
                where ms.email = ?
                """, email);
    }

    private Optional<AuthMember> findByLoginIdAndNicknameAndEmail(String loginId, String nickname, String email) {
        return findOne("""
                select ms.member_id, ms.login_id, ms.email, ms.nickname, ms.status, ms.login_fail_count,
                       ms.created_at
                from member_snapshot ms
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
                    toLocalDateTime(rs.getTimestamp("created_at"))
            ), args));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private MemberProfile findActiveProfileByLoginId(String loginId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select ms.member_id,
                           ms.login_id,
                           ms.email,
                           ms.nickname,
                           ms.phone,
                           ms.email_verified,
                           ms.profile_image_url
                    from member_snapshot ms
                    where ms.login_id = ? and ms.status = 'ACTIVE'
                    """, (rs, rowNum) -> new MemberProfile(
                    rs.getLong("member_id"),
                    rs.getString("login_id"),
                    rs.getString("email"),
                    rs.getString("nickname"),
                    rs.getString("phone"),
                    rs.getBoolean("email_verified"),
                    rs.getString("profile_image_url")
            ), loginId);
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid access token.");
        }
    }

    private String currentUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Authorization token is required.");
        }
        String token = authorizationHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            token = token.substring("Bearer ".length()).trim();
        }
        if (token.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Authorization token is required.");
        }
        return jwtTokenProvider.validateAccessToken(token);
    }

    private MemberProfileResponse toProfileResponse(MemberProfile profile) {
        return new MemberProfileResponse(
                profile.loginId(),
                profile.nickname(),
                profile.phone(),
                profile.email(),
                profile.emailVerified(),
                profile.profileImageUrl()
        );
    }

    private String resolveNickname(MemberProfile profile, String nickname) {
        if (nickname == null) {
            return profile.nickname();
        }
        String normalized = nickname.trim();
        if (normalized.length() < 2 || normalized.length() > 10) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Nickname must be 2 to 10 characters.");
        }
        if (!normalized.equals(profile.nickname())
                && exists("select count(*) from member_snapshot where nickname = ? and member_id <> ? and status <> 'DELETED'", normalized, profile.memberId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Nickname is already in use.");
        }
        return normalized;
    }

    private String resolvePhone(MemberProfile profile, String phone) {
        if (phone == null) {
            return profile.phone();
        }
        String normalized = phone.trim();
        if (!normalized.matches("^010-\\d{4}-\\d{4}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Phone must match 010-0000-0000.");
        }
        return normalized;
    }

    private String resolveEmail(MemberProfile profile, String email) {
        if (email == null) {
            return profile.email();
        }
        String normalized = email.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        if (normalized.equals(profile.email())) {
            return profile.email();
        }
        if (exists("select count(*) from member_snapshot where email = ? and member_id <> ? and status <> 'DELETED'", normalized, profile.memberId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        return normalized;
    }

    private String resolveProfileImageUrl(MemberProfile profile, String profileImageUrl) {
        if (profileImageUrl == null) {
            return profile.profileImageUrl();
        }
        String normalized = profileImageUrl.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean exists(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
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

    private String formatProcessedAt(OnPremCommandData result) {
        LocalDateTime processedAt = result.processedAt() == null ? LocalDateTime.now() : result.processedAt();
        return processedAt.format(DATE_TIME_FORMAT);
    }

    private record AuthMember(
            long memberId,
            String loginId,
            String email,
            String nickname,
            String status,
            int loginFailCount,
            LocalDateTime createdAt
    ) {
    }

    private record MemberProfile(
            long memberId,
            String loginId,
            String email,
            String nickname,
            String phone,
            boolean emailVerified,
            String profileImageUrl
    ) {
    }
}
