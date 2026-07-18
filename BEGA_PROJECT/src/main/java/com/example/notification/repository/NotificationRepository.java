package com.example.notification.repository;

import com.example.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 사용자별 알림 목록 (최신순) — 페이징 지원
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 사용자별 알림 목록 (최신순) — 비페이징(legacy 호환용; 신규 호출 사용 금지)
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 읽지 않은 알림 개수
    Long countByUserIdAndIsReadFalse(Long userId);

    // 사용자 전체 알림 일괄 읽음 처리
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);
}
