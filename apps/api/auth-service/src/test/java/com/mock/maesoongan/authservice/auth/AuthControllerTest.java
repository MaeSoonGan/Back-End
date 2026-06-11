package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.AvailabilityResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ExpiresInResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.FindIdResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.RegisterResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ResetPasswordResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.TokenResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifiedResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.VerifyResetResponse;
import com.mock.maesoongan.authservice.common.BusinessException;
import com.mock.maesoongan.authservice.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse("access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "testuser",
                                  "password": "Password1!",
                                  "keepLogin": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                .andExpect(jsonPath("$.data.refreshToken", is("refresh-token")));
    }

    @Test
    void loginReturnsUnauthorizedWhenCredentialIsInvalid() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid userId or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "testuser",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void reissueReturnsRotatedTokens() throws Exception {
        when(authService.reissue(any())).thenReturn(new TokenResponse("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "old-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", is("new-access-token")))
                .andExpect(jsonPath("$.data.refreshToken", is("new-refresh-token")));
    }

    @Test
    void checkIdReturnsAvailability() throws Exception {
        when(authService.checkId("testuser")).thenReturn(new AvailabilityResponse(true));

        mockMvc.perform(get("/api/auth/check-id").param("userId", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available", is(true)));
    }

    @Test
    void sendCodeReturnsExpirationSeconds() throws Exception {
        when(authService.sendEmailCode(any())).thenReturn(new ExpiresInResponse(180));

        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "purpose": "signup"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresIn", is(180)));
    }

    @Test
    void verifyCodeReturnsVerified() throws Exception {
        when(authService.verifyEmailCode(any())).thenReturn(new VerifiedResponse(true));

        mockMvc.perform(post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified", is(true)));
    }

    @Test
    void registerReturnsCreated() throws Exception {
        when(authService.register(any())).thenReturn(new RegisterResponse("testuser", "tester", "test@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "testuser",
                                  "password": "Password1!",
                                  "email": "test@example.com",
                                  "nickname": "tester",
                                  "phone": "010-1234-5678",
                                  "termsAgreed": true,
                                  "privacyAgreed": true,
                                  "marketingAgreed": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId", is("testuser")))
                .andExpect(jsonPath("$.data.nickname", is("tester")));
    }

    @Test
    void findIdReturnsMaskedUserId() throws Exception {
        when(authService.findId(any())).thenReturn(new FindIdResponse("test****", "test***@example.com", "2026.06.10"));

        mockMvc.perform(post("/api/auth/find-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedUserId", is("test****")));
    }

    @Test
    void verifyResetReturnsResetToken() throws Exception {
        when(authService.verifyReset(any())).thenReturn(new VerifyResetResponse("rst-token", "test****"));

        mockMvc.perform(post("/api/auth/verify-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "testuser",
                                  "name": "tester",
                                  "email": "test@example.com",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resetToken", is("rst-token")));
    }

    @Test
    void resetPasswordReturnsChangedAt() throws Exception {
        when(authService.resetPassword(any())).thenReturn(new ResetPasswordResponse("test****", "2026.06.10 10:00"));

        mockMvc.perform(patch("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resetToken": "rst-token",
                                  "newPassword": "NewPassword1!",
                                  "newPasswordConfirm": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedUserId", is("test****")))
                .andExpect(jsonPath("$.data.changedAt", is("2026.06.10 10:00")));

        verify(authService).resetPassword(any());
    }
}
