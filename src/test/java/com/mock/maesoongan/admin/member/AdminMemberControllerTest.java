package com.mock.maesoongan.admin.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMemberControllerTest {

    private static final String ADMIN_TOKEN = "Bearer admin-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getSummaryReturnsCounts() throws Exception {
        mockMvc.perform(get("/api/admin/members/summary")
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(1234))
                .andExpect(jsonPath("$.data.activeCount").value(1198))
                .andExpect(jsonPath("$.data.suspendedCount").value(36))
                .andExpect(jsonPath("$.data.todayJoinCount").value(12));
    }

    @Test
    void getMembersReturnsPagedMembers() throws Exception {
        mockMvc.perform(get("/api/admin/members")
                        .header("Authorization", ADMIN_TOKEN)
                        .param("page", "0")
                        .param("size", "6")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(6)))
                .andExpect(jsonPath("$.data.content[0].email").value("hong***@naver.com"))
                .andExpect(jsonPath("$.data.currentPage").value(0));
    }

    @Test
    void getMemberReturnsDetail() throws Exception {
        mockMvc.perform(get("/api/admin/members/{userId}", 1)
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.accountId").value("user001"))
                .andExpect(jsonPath("$.data.email").value("hong@naver.com"));
    }

    @Test
    void exportCsvReturnsAttachment() throws Exception {
        mockMvc.perform(get("/api/admin/members/export")
                        .header("Authorization", ADMIN_TOKEN)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"admin-members.csv\""));
    }

    @Test
    void invalidStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/members")
                        .header("Authorization", ADMIN_TOKEN)
                        .param("page", "0")
                        .param("size", "6")
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void adminMembersWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/members/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
