package com.mock.maesoongan.notificationapi.notice.service;

import com.mock.maesoongan.notificationapi.common.BusinessException;
import com.mock.maesoongan.notificationapi.notice.domain.Notice;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeDetailResponse;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeItem;
import com.mock.maesoongan.notificationapi.notice.dto.NoticeDtos.NoticeListResponse;
import com.mock.maesoongan.notificationapi.notice.repository.NoticeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    @Transactional(readOnly = true)
    public NoticeListResponse getNotices() {
        List<NoticeItem> items = noticeRepository.findVisibleNotices(LocalDateTime.now())
                .stream()
                .map(notice -> new NoticeItem(
                        notice.getId(),
                        notice.getTitle(),
                        notice.getContent(),
                        notice.isPinned(),
                        notice.getCreatedAt()
                ))
                .toList();
        return new NoticeListResponse(items);
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNotice(Long noticeId) {
        Notice notice = noticeRepository.findVisibleNotice(noticeId, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notice not found"));
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.getCreatedAt()
        );
    }
}
