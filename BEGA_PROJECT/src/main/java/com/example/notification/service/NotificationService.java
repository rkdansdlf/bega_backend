package com.example.notification.service;

import com.example.notification.dto.NotificationDTO;
import com.example.notification.entity.Notification;
import com.example.notification.exception.NotificationNotFoundException;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import com.example.mate.exception.UnauthorizedAccessException;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // 알림 생성
    @Transactional
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

        // WebSocket으로 실시간 알림 전송 (트랜잭션 커밋 후 전송)
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            messagingTemplate.convertAndSend(
                                    "/topic/notifications/" + userId,
                                    dto);
                            log.info("알림 전송 성공 (After Commit): userId={}, type={}", userId, type);
                        } catch (Exception e) {
                            log.error("알림 전송 실패: {}", e.getMessage());
                        }
                    }
                });

    }

    // 사용자 알림 목록 조회 (Principal 버전)
    @Transactional(readOnly = true)
    public List<NotificationDTO.Response> getMyNotifications(@NonNull Long userId) {
        ensureAuthenticatedUser(userId);
        return Objects.requireNonNull(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
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

    // 읽지 않은 알림 개수 (userId 경로 호환용)
    @Transactional(readOnly = true)
    public Long getUnreadCountByUserId(@NonNull Long userId, @NonNull Long currentUserId) {
        ensureAuthenticatedUser(currentUserId);
        if (!currentUserId.equals(userId)) {
            throw new UnauthorizedAccessException("본인 알림만 조회할 수 있습니다.");
        }
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
            throw new UnauthorizedAccessException("인증이 필요합니다.");
        }
    }
}
