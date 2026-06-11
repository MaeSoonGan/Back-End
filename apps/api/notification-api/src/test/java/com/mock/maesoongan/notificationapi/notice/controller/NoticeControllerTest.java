package com.mock.maesoongan.notificationapi.notice.controller;

import com.mock.maesoongan.notificationapi.common.BusinessException;
import com.mock.maesoongan.notificationapi.common.GlobalExceptionHandler;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeDetailResponse;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeItem;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeListResponse;
import com.mock.maesoongan.notificationapi.notice.service.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NoticeControllerTest {

    private NoticeService noticeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        noticeService = mock(NoticeService.class);
        mockMvc = standaloneSetup(new NoticeController(noticeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getNoticesReturnsPagedNoticeList() throws Exception {
        when(noticeService.getNotices(0, 5)).thenReturn(new NoticeListResponse(
                List.of(new NoticeItem(
                        1L,
                        "Service notice",
                        "Notice content",
                        true,
                        "Admin",
                        LocalDateTime.of(2026, 6, 10, 10, 0)
                )),
                1L,
                1,
                0
        ));

        mockMvc.perform(get("/api/notices").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(1)))
                .andExpect(jsonPath("$.data.content[0].noticeId", is(1)))
                .andExpect(jsonPath("$.data.content[0].isPinned", is(true)));

        verify(noticeService).getNotices(0, 5);
    }

    @Test
    void getNoticeReturnsNoticeDetail() throws Exception {
        when(noticeService.getNotice(1L)).thenReturn(new NoticeDetailResponse(
                1L,
                "Service notice",
                "Notice content",
                false,
                "Admin",
                LocalDateTime.of(2026, 6, 10, 10, 0)
        ));

        mockMvc.perform(get("/api/notices/{noticeId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.noticeId", is(1)))
                .andExpect(jsonPath("$.data.title", is("Service notice")));
    }

    @Test
    void getNoticeReturnsNotFoundWhenNoticeDoesNotExist() throws Exception {
        when(noticeService.getNotice(999L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notice not found"));

        mockMvc.perform(get("/api/notices/{noticeId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }
}
