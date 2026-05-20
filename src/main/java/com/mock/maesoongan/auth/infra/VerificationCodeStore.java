package com.mock.maesoongan.auth.infra;

import com.mock.maesoongan.common.exception.BusinessException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class VerificationCodeStore {

    private static final int EXPIRES_IN_SECONDS = 180;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(VerificationCodeStore.class);

    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final Set<String> verifiedSignupEmails = ConcurrentHashMap.newKeySet();

    public String issue(String channel, String target, String purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.put(key(channel, target, purpose), new CodeEntry(code, LocalDateTime.now().plusSeconds(EXPIRES_IN_SECONDS)));
        log.info("[VERIFICATION CODE ISSUED] channel={}, target={}, purpose={}, code={}", channel, target, purpose, code);
        return code;
    }

    public void verify(String channel, String target, String purpose, String code) {
        CodeEntry entry = codes.get(key(channel, target, purpose));
        if (entry == null) {
            log.info("[VERIFICATION CODE MISSING] channel={}, target={}, purpose={}, input={}",
                    channel, target, purpose, code);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Verification code does not match.");
        }
        if (entry.expiresAt().isBefore(LocalDateTime.now())) {
            log.info("[VERIFICATION CODE EXPIRED] channel={}, target={}, purpose={}, input={}",
                    channel, target, purpose, code);
            throw new BusinessException(HttpStatus.GONE, "Verification code has expired.");
        }
        if (!entry.code().equals(code)) {
            log.info("[VERIFICATION CODE MISMATCH] channel={}, target={}, purpose={}, expected={}, input={}",
                    channel, target, purpose, entry.code(), code);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Verification code does not match.");
        }
        log.info("[VERIFICATION CODE VERIFIED] channel={}, target={}, purpose={}", channel, target, purpose);
    }

    public String verifyAny(String channel, String target, String code, String... purposes) {
        BusinessException lastException = null;
        for (String purpose : purposes) {
            try {
                verify(channel, target, purpose, code);
                return purpose;
            } catch (BusinessException exception) {
                lastException = exception;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "Verification code does not match.");
    }

    public void markSignupEmailVerified(String email) {
        verifiedSignupEmails.add(email);
    }

    public boolean isSignupEmailVerified(String email) {
        return verifiedSignupEmails.contains(email);
    }

    public int expiresInSeconds() {
        return EXPIRES_IN_SECONDS;
    }

    private String key(String channel, String target, String purpose) {
        return channel + ":" + purpose + ":" + target;
    }

    private record CodeEntry(String code, LocalDateTime expiresAt) {
    }
}
