package com.mock.maesoongan.notificationapi.notice.repository;

import com.mock.maesoongan.notificationapi.notice.domain.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 게시 중(PUBLISHED)이고 노출 기간 내인 공지만 페이징 조회한다.
    // 고정(pinned) 공지가 상단, 그 안에서 최신순.
    @Query("""
            select n
            from Notice n
            where n.status = 'PUBLISHED'
              and (n.startAt is null or n.startAt <= :now)
              and (n.endAt is null or n.endAt > :now)
            order by n.pinned desc, n.createdAt desc, n.id desc
            """)
    Page<Notice> findVisibleNotices(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            select n
            from Notice n
            where n.id = :id
              and n.status = 'PUBLISHED'
              and (n.startAt is null or n.startAt <= :now)
              and (n.endAt is null or n.endAt > :now)
            """)
    Optional<Notice> findVisibleNotice(@Param("id") Long id, @Param("now") LocalDateTime now);
}
