package com.example.notification.repository;

import com.example.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import jakarta.persistence.LockModeType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByRelatedIdAndTypeIn(Long relatedId, Collection<Notification.NotificationType> types);

    // 사용자별 알림 목록 (최신순) — 페이징 지원
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 사용자별 알림 목록 (최신순) — 비페이징(legacy 호환용; 신규 호출 사용 금지)
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 읽지 않은 알림 개수
    Long countByUserIdAndIsReadFalse(Long userId);

    // 조회 스냅샷의 ID만 잠가, 응답 ID와 실제 변경 대상을 일치시킨다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n "
            + "WHERE n.userId = :userId AND n.id IN :notificationIds "
            + "AND n.isRead = false "
            + "ORDER BY n.createdAt ASC, n.id ASC")
    List<Notification> findUnreadForUserByIdsForUpdate(
            @Param("userId") Long userId,
            @Param("notificationIds") Collection<Long> notificationIds);
}
