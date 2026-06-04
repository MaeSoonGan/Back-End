package com.mock.maesoongan.notificationapi.notice.repository;

import com.mock.maesoongan.notificationapi.notice.domain.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 게시 중(PUBLISHED)이고 노출 기간 내인 공지만 조회한다.
    @Query("""
            select n
            from Notice n
            where n.status = 'PUBLISHED'
              and (n.startAt is null or n.startAt <= :now)
              and (n.endAt is null or n.endAt > :now)
            order by n.pinned desc, n.createdAt desc, n.id desc
            """)
    List<Notice> findVisibleNotices(@Param("now") LocalDateTime now);

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
