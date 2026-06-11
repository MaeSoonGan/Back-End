package com.mock.maesoongan.adminservice;

import com.mock.maesoongan.adminservice.common.BusinessException;
import com.mock.maesoongan.adminservice.notice.AdminNoticeDtos;
import com.mock.maesoongan.adminservice.notice.AdminNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminNoticeServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AdminNoticeService adminNoticeService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        adminNoticeService = new AdminNoticeService(jdbcTemplate);
    }

    @Test
    void getNoticesThrowsBadRequestWhenPageIsInvalid() {
        assertThatThrownBy(() -> adminNoticeService.getNotices(null, "ALL", -1, 10, null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("BAD_REQUEST");
                });
    }

    @Test
    void getNoticesThrowsBadRequestWhenStatusIsInvalid() {
        assertThatThrownBy(() -> adminNoticeService.getNotices(null, "INVALID", 0, 10, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getNoticeThrowsNotFoundWhenNoticeDoesNotExist() {
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate)
                .queryForObject(anyString(), any(RowMapper.class), any(Object[].class));

        assertThatThrownBy(() -> adminNoticeService.getNotice(999L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void createNoticeThrowsBadRequestWhenPeriodIsInvalid() {
        AdminNoticeDtos.NoticeCreateRequest request = new AdminNoticeDtos.NoticeCreateRequest(
                "Notice",
                "content",
                false,
                "PUBLISHED",
                LocalDateTime.of(2026, 6, 12, 9, 0),
                LocalDateTime.of(2026, 6, 11, 9, 0)
        );

        assertThatThrownBy(() -> adminNoticeService.createNotice(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deleteNoticeReturnsDeletedStatus() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        AdminNoticeDtos.NoticeMutationResponse response = adminNoticeService.deleteNotice(5L);

        assertThat(response.noticeId()).isEqualTo(5L);
        assertThat(response.status()).isEqualTo("DELETED");
        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
    }
}
