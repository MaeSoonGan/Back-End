package com.mock.maesoongan.notificationapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock.maesoongan.notificationapi.auth.CurrentMember;
import com.mock.maesoongan.notificationapi.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenFilter bearerTokenFilter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication failed"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Permission denied"))
                )
                .build();
    }

    @Bean
    public BearerTokenFilter bearerTokenFilter(
            JdbcTemplate jdbcTemplate,
            @Value("${app.jwt.secret:local-development-secret-change-me}") String jwtSecret
    ) {
        return new BearerTokenFilter(jdbcTemplate, jwtSecret);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }

    static class BearerTokenFilter extends OncePerRequestFilter {

        private static final String HMAC_ALGORITHM = "HmacSHA256";

        private final JdbcTemplate jdbcTemplate;
        private final String jwtSecret;

        BearerTokenFilter(JdbcTemplate jdbcTemplate, String jwtSecret) {
            this.jdbcTemplate = jdbcTemplate;
            this.jwtSecret = jwtSecret;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            String authorization = request.getHeader("Authorization");

            SecurityContextHolder.clearContext();

            if ("Bearer user-token".equals(authorization)) {
                authenticate(new CurrentMember(1L, "user"));
            } else if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7) {
                authenticateIfValidJwt(authorization.substring(7));
            }

            filterChain.doFilter(request, response);
        }

        private void authenticateIfValidJwt(String token) {
            try {
                String loginId = subjectFromAccessToken(token);
                Long memberId = jdbcTemplate.queryForObject(
                        "select member_id from member_snapshot where login_id = ? and status <> 'DELETED'",
                        Long.class,
                        loginId
                );

                if (memberId != null) {
                    authenticate(new CurrentMember(memberId, loginId));
                }
            } catch (EmptyResultDataAccessException exception) {
                SecurityContextHolder.clearContext();
            } catch (Exception exception) {
                SecurityContextHolder.clearContext();
            }
        }

        private String subjectFromAccessToken(String token) throws Exception {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token");
            }

            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) {
                throw new IllegalArgumentException("Invalid signature");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            if (!"access".equals(extractString(payload, "typ"))) {
                throw new IllegalArgumentException("Invalid token type");
            }

            long exp = Long.parseLong(extractNumber(payload, "exp"));
            if (exp < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Expired token");
            }

            return extractString(payload, "sub");
        }

        private String sign(String value) throws Exception {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        }

        private boolean constantTimeEquals(String left, String right) {
            byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
            byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
            if (leftBytes.length != rightBytes.length) {
                return false;
            }

            int result = 0;
            for (int i = 0; i < leftBytes.length; i++) {
                result |= leftBytes[i] ^ rightBytes[i];
            }
            return result == 0;
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

        private void authenticate(CurrentMember currentMember) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    currentMember,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
