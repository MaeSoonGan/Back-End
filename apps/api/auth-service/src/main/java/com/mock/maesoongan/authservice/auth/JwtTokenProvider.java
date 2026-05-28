package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;

    public JwtTokenProvider(@Value("${app.jwt.secret:local-development-secret-change-me}") String secret) {
        this.secret = secret;
    }

    public String createAccessToken(String subject) {
        return createToken(subject, "access", 3600);
    }

    public String createRefreshToken(String subject, boolean keepLogin) {
        return createToken(subject, "refresh", keepLogin ? 60L * 60 * 24 * 30 : 60L * 60 * 24 * 7);
    }

    public String validateRefreshToken(String refreshToken) {
        Map<String, Object> claims = parse(refreshToken, "Invalid refresh token.");
        if (!"refresh".equals(claims.get("typ"))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }
        return String.valueOf(claims.get("sub"));
    }

    public String createResetToken(String subject) {
        return "rst_" + createToken(subject, "reset", 600);
    }

    public String validateResetToken(String resetToken) {
        if (resetToken == null || !resetToken.startsWith("rst_")) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid reset token.");
        }
        Map<String, Object> claims = parse(resetToken.substring(4), "Invalid reset token.");
        if (!"reset".equals(claims.get("typ"))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid reset token.");
        }
        return String.valueOf(claims.get("sub"));
    }

    private String createToken(String subject, String type, long ttlSeconds) {
        try {
            long now = Instant.now().getEpochSecond();
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String payload = "{\"sub\":\"" + subject + "\",\"typ\":\"" + type
                    + "\",\"iat\":" + now + ",\"exp\":" + (now + ttlSeconds)
                    + ",\"jti\":\"" + UUID.randomUUID() + "\"}";

            String unsigned = encode(header) + "." + encode(payload);
            return unsigned + "." + sign(unsigned);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create JWT.", exception);
        }
    }

    private Map<String, Object> parse(String token, String invalidMessage) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token");
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) {
                throw new IllegalArgumentException("Invalid signature");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(extractNumber(payload, "exp"));
            if (exp < Instant.now().getEpochSecond()) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, invalidMessage);
            }
            return Map.of(
                    "sub", extractString(payload, "sub"),
                    "typ", extractString(payload, "typ"),
                    "exp", exp
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, invalidMessage);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigestSupport.equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new IllegalArgumentException("Missing claim");
        }
        int valueStart = start + pattern.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            throw new IllegalArgumentException("Invalid claim");
        }
        return json.substring(valueStart, valueEnd);
    }

    private String extractNumber(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new IllegalArgumentException("Missing claim");
        }
        int valueStart = start + pattern.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd);
    }
}
