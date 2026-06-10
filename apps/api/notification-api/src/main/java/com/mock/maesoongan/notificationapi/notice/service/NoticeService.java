package com.mock.maesoongan.notificationapi.notice.service;

import com.mock.maesoongan.notificationapi.common.BusinessException;
import com.mock.maesoongan.notificationapi.notice.domain.Notice;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeDetailResponse;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeItem;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeListResponse;
import com.mock.maesoongan.notificationapi.notice.repository.NoticeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class NoticeService {

    private static final String DEFAULT_AUTHOR = "운영자";

    private final NoticeRepository noticeRepository;
    private final JdbcTemplate jdbcTemplate;

    public NoticeService(NoticeRepository noticeRepository, JdbcTemplate jdbcTemplate) {
        this.noticeRepository = noticeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public NoticeListResponse getNotices(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notice> noticePage = noticeRepository.findVisibleNotices(LocalDateTime.now(), pageable);

        Map<Long, String> authorNames = resolveAuthorNames(
                noticePage.getContent().stream().map(Notice::getAdminId).toList()
        );

        Page<NoticeItem> result = noticePage.map(notice -> new NoticeItem(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                authorName(authorNames, notice.getAdminId()),
                notice.getCreatedAt()
        ));
        return new NoticeListResponse(
                result.getContent(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber()
        );
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNotice(Long noticeId) {
        Notice notice = noticeRepository.findVisibleNotice(noticeId, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notice not found"));
        Map<Long, String> authorNames = resolveAuthorNames(
                notice.getAdminId() == null ? List.of() : List.of(notice.getAdminId())
        );
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                authorName(authorNames, notice.getAdminId()),
                notice.getCreatedAt()
        );
    }

    // admin_id -> 작성자 표시명(nickname, 없으면 login_id) 일괄 조회
    private Map<Long, String> resolveAuthorNames(List<Long> adminIds) {
        List<Long> ids = adminIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, String> names = new HashMap<>();
        jdbcTemplate.query(
                "select id, coalesce(nickname, login_id, '" + DEFAULT_AUTHOR + "') as name from admin where id in (" + placeholders + ")",
                rs -> { names.put(rs.getLong("id"), rs.getString("name")); },
                ids.toArray()
        );
        return names;
    }

    private String authorName(Map<Long, String> authorNames, Long adminId) {
        if (adminId == null) {
            return DEFAULT_AUTHOR;
        }
        return authorNames.getOrDefault(adminId, DEFAULT_AUTHOR);
    }
}
