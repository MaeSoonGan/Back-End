package com.mock.maesoongan.notificationapi.notice.service;

import com.mock.maesoongan.notificationapi.common.BusinessException;
import com.mock.maesoongan.notificationapi.notice.domain.Notice;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeDetailResponse;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeListResponse;
import com.mock.maesoongan.notificationapi.notice.repository.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeServiceTest {

    private NoticeRepository noticeRepository;
    private JdbcTemplate jdbcTemplate;
    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeRepository = mock(NoticeRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        noticeService = new NoticeService(noticeRepository, jdbcTemplate);
    }

    @Test
    void getNoticesReturnsPagedVisibleNoticesWithAuthorName() {
        Notice notice = notice(1L, 10L, "Service notice", "Notice content", true);
        when(noticeRepository.findVisibleNotices(any(), any()))
                .thenReturn(new PageImpl<>(List.of(notice), PageRequest.of(0, 5), 1));
        mockAdminName(10L, "Admin");

        NoticeListResponse response = noticeService.getNotices(0, 5);

        assertEquals(1L, response.totalElements());
        assertEquals(1, response.totalPages());
        assertEquals(0, response.currentPage());
        assertEquals(1L, response.content().get(0).noticeId());
        assertEquals("Admin", response.content().get(0).authorName());
        assertTrue(response.content().get(0).isPinned());
        verify(noticeRepository).findVisibleNotices(any(), any());
    }

    @Test
    void getNoticeReturnsVisibleNoticeDetail() {
        Notice notice = notice(1L, 10L, "Service notice", "Notice content", false);
        when(noticeRepository.findVisibleNotice(any(), any())).thenReturn(Optional.of(notice));
        mockAdminName(10L, "Admin");

        NoticeDetailResponse response = noticeService.getNotice(1L);

        assertEquals(1L, response.noticeId());
        assertEquals("Service notice", response.title());
        assertEquals("Notice content", response.content());
        assertEquals("Admin", response.authorName());
    }

    @Test
    void getNoticeThrowsNotFoundWhenNoticeIsNotVisible() {
        when(noticeRepository.findVisibleNotice(any(), any())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> noticeService.getNotice(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.status());
        assertEquals("NOT_FOUND", exception.code());
    }

    private void mockAdminName(Long adminId, String name) {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(adminId);
            when(resultSet.getString("name")).thenReturn(name);
            handler.processRow(resultSet);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    private Notice notice(Long id, Long adminId, String title, String content, boolean pinned) {
        Notice notice = newNotice();
        ReflectionTestUtils.setField(notice, "id", id);
        ReflectionTestUtils.setField(notice, "adminId", adminId);
        ReflectionTestUtils.setField(notice, "title", title);
        ReflectionTestUtils.setField(notice, "content", content);
        ReflectionTestUtils.setField(notice, "pinned", pinned);
        ReflectionTestUtils.setField(notice, "status", "PUBLISHED");
        ReflectionTestUtils.setField(notice, "createdAt", LocalDateTime.of(2026, 6, 10, 10, 0));
        return notice;
    }

    private Notice newNotice() {
        try {
            Constructor<Notice> constructor = Notice.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create notice test fixture", exception);
        }
    }
}
