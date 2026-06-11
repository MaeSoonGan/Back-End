package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.ChangePasswordResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.MemberProfileResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.ProfileImageUploadUrlResponse;
import com.mock.maesoongan.authservice.auth.AuthDtos.WithdrawMemberResponse;
import com.mock.maesoongan.authservice.common.BusinessException;
import com.mock.maesoongan.authservice.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MemberProfileControllerTest {

    private AuthService authService;
    private ProfileImageService profileImageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        profileImageService = mock(ProfileImageService.class);
        mockMvc = standaloneSetup(new MemberProfileController(authService, profileImageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMyProfileReturnsProfile() throws Exception {
        String authorization = "Bearer access-token";
        when(authService.getMyProfile(authorization)).thenReturn(profileResponse());

        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is("testuser")))
                .andExpect(jsonPath("$.data.emailVerified", is(true)));
    }

    @Test
    void getMyProfileReturnsUnauthorizedWhenBearerTokenIsMissing() throws Exception {
        when(authService.getMyProfile(null))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "Authorization bearer token is required."));

        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void updateMyProfileReturnsUpdatedProfile() throws Exception {
        String authorization = "Bearer access-token";
        when(authService.updateMyProfile(any(), any())).thenReturn(new MemberProfileResponse(
                "testuser",
                "newNick",
                "010-2222-3333",
                "test@example.com",
                true,
                "https://cdn.example.com/profile.png"
        ));

        mockMvc.perform(patch("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "newNick",
                                  "phone": "010-2222-3333",
                                  "profileImageUrl": "https://cdn.example.com/profile.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname", is("newNick")))
                .andExpect(jsonPath("$.data.phone", is("010-2222-3333")));
    }

    @Test
    void changeMyPasswordReturnsChangedAt() throws Exception {
        when(authService.changeMyPassword(any(), any()))
                .thenReturn(new ChangePasswordResponse("2026.06.10 10:00"));

        mockMvc.perform(patch("/api/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Password1!",
                                  "newPassword": "NewPassword1!",
                                  "newPasswordConfirm": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changedAt", is("2026.06.10 10:00")));
    }

    @Test
    void withdrawMeReturnsWithdrawnAt() throws Exception {
        when(authService.withdrawMe(any(), any()))
                .thenReturn(new WithdrawMemberResponse("2026.06.10 10:00"));

        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawnAt", is("2026.06.10 10:00")));
    }

    @Test
    void getProfileImageUploadUrlReturnsUploadAndImageUrl() throws Exception {
        when(authService.currentMemberId("Bearer access-token")).thenReturn(7L);
        when(profileImageService.createUploadUrl(7L, "image/png")).thenReturn(new ProfileImageUploadUrlResponse(
                "https://upload.example.com",
                "https://cdn.example.com/profile.png"
        ));

        mockMvc.perform(post("/api/members/me/profile-image/presigned-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentType": "image/png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl", is("https://upload.example.com")))
                .andExpect(jsonPath("$.data.imageUrl", is("https://cdn.example.com/profile.png")));

        verify(profileImageService).createUploadUrl(7L, "image/png");
    }

    private MemberProfileResponse profileResponse() {
        return new MemberProfileResponse(
                "testuser",
                "tester",
                "010-1234-5678",
                "test@example.com",
                true,
                null
        );
    }
}
