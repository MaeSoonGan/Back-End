package com.mock.maesoongan.auth.service;

import com.mock.maesoongan.auth.dto.AuthDtos.AvailabilityResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.ExpiresInResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.FindIdRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.FindIdResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.LoginRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.RegisterRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.RegisterResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.ResetPasswordRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.ResetPasswordResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.SendEmailCodeRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.TokenResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifiedResponse;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifyCodeRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifyResetRequest;
import com.mock.maesoongan.auth.dto.AuthDtos.VerifyResetResponse;
import com.mock.maesoongan.auth.infra.EmailCodeSender;
import com.mock.maesoongan.auth.infra.JwtTokenProvider;
import com.mock.maesoongan.auth.infra.VerificationCodeStore;
import com.mock.maesoongan.common.exception.BusinessException;
import com.mock.maesoongan.member.entity.Member;
import com.mock.maesoongan.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final VerificationCodeStore verificationCodeStore;
    private final EmailCodeSender emailCodeSender;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid userId or password."));

        if (member.isLocked()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account is locked.");
        }

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            member.increaseLoginFailCount();
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid userId or password.");
        }

        member.resetLoginFailCount();
        return new TokenResponse(
                jwtTokenProvider.createAccessToken(member.getLoginId()),
                jwtTokenProvider.createRefreshToken(member.getLoginId(), request.keepLoginValue())
        );
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkId(String userId) {
        validateUserId(userId);
        return new AvailabilityResponse(!memberRepository.existsByLoginId(userId));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkNickname(String nickname) {
        if (nickname == null || nickname.length() < 2 || nickname.length() > 10) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Nickname must be 2 to 10 characters.");
        }
        return new AvailabilityResponse(!memberRepository.existsByNickname(nickname));
    }

    @Transactional(readOnly = true)
    public ExpiresInResponse sendEmailCode(SendEmailCodeRequest request) {
        if (SIGNUP.equals(request.purpose()) && memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        if ((FIND_ID.equals(request.purpose()) || RESET_PASSWORD.equals(request.purpose()))
                && !memberRepository.existsByEmail(request.email())) {
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
        if (memberRepository.existsByLoginId(request.userId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "UserId is already in use.");
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Nickname is already in use.");
        }

        Member member = Member.create(
                request.userId(),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.nickname(),
                request.phone()
        );
        member.verifyEmail();
        Member saved = memberRepository.save(member);
        return new RegisterResponse(saved.getLoginId(), saved.getNickname(), saved.getEmail());
    }

    @Transactional(readOnly = true)
    public FindIdResponse findId(FindIdRequest request) {
        verificationCodeStore.verify(EMAIL, request.email(), FIND_ID, request.code());
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Email is not registered."));

        return new FindIdResponse(
                maskUserId(member.getLoginId()),
                maskEmail(member.getEmail()),
                member.getCreatedAt().format(DATE_FORMAT)
        );
    }

    @Transactional(readOnly = true)
    public VerifyResetResponse verifyReset(VerifyResetRequest request) {
        verificationCodeStore.verify(EMAIL, request.email(), RESET_PASSWORD, request.code());
        Member member = memberRepository.findByLoginIdAndNicknameAndEmail(request.userId(), request.name(), request.email())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "No matching user found."));

        return new VerifyResetResponse(
                jwtTokenProvider.createResetToken(member.getLoginId()),
                maskUserId(member.getLoginId())
        );
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "New password confirmation does not match.");
        }

        String userId = jwtTokenProvider.validateResetToken(request.resetToken());
        Member member = memberRepository.findByLoginId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid reset token."));

        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new BusinessException(HttpStatus.CONFLICT, "New password must be different from the old password.");
        }

        LocalDateTime changedAt = LocalDateTime.now();
        member.changePassword(passwordEncoder.encode(request.newPassword()));
        return new ResetPasswordResponse(maskUserId(member.getLoginId()), changedAt.format(DATE_TIME_FORMAT));
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
}
