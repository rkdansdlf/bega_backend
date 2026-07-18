package com.example.notification.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.realtime.RealtimeOutboxWriter;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.notification.dto.NotificationDTO;
import com.example.notification.entity.Notification;
import com.example.notification.exception.NotificationNotFoundException;
import com.example.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RealtimeOutboxWriter realtimeOutboxWriter;

    // 알림 생성
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(
            @NonNull Long userId,
            @NonNull Notification.NotificationType type,
            @NonNull String title,
            @NonNull String message,
            Long relatedId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .relatedId(relatedId)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        // DTO 생성
        NotificationDTO.Response dto = NotificationDTO.Response.from(saved);

        realtimeOutboxWriter.sendToUser(
                String.valueOf(userId),
                "/queue/notifications",
                dto);

    }

    // 사용자 알림 목록 조회 — 기본 30건만 반환 (오래된 알림 무제한 로딩 방지)
    public static final int DEFAULT_NOTIFICATION_PAGE_SIZE = 30;

    @Transactional(readOnly = true)
    public List<NotificationDTO.Response> getMyNotifications(@NonNull Long userId) {
        return getMyNotifications(userId, PageRequest.of(0, DEFAULT_NOTIFICATION_PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO.Response> getMyNotifications(@NonNull Long userId, @NonNull Pageable pageable) {
        ensureAuthenticatedUser(userId);
        return Objects.requireNonNull(notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .getContent()
                .stream()
                .map(NotificationDTO.Response::from)
                .collect(Collectors.toList()));
    }

    // 읽지 않은 알림 개수 (Principal 버전)
    @Transactional(readOnly = true)
    public Long getMyUnreadCount(@NonNull Long userId) {
        ensureAuthenticatedUser(userId);
        return Objects.requireNonNull(notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    // 알림 읽음 처리
    @Transactional
    public void markAsRead(@NonNull Long notificationId, @NonNull Long userId) {
        if (notificationId == null)
            return;
        ensureAuthenticatedUser(userId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("자신의 알림만 읽음 처리할 수 있습니다.");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    // 전체 알림 일괄 읽음 처리
    @Transactional
    public void markAllAsRead(@NonNull Long userId) {
        ensureAuthenticatedUser(userId);
        notificationRepository.markAllAsReadByUserId(userId);
    }

    // 알림 삭제
    @Transactional
    public void deleteNotification(@NonNull Long notificationId, @NonNull Long userId) {
        if (notificationId == null)
            return;
        ensureAuthenticatedUser(userId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("자신의 알림만 삭제할 수 있습니다.");
        }

        notificationRepository.delete(notification);
    }

    private void ensureAuthenticatedUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
    }
}
