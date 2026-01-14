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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // 알림 생성
    @Transactional
    @SuppressWarnings("null")
    public void createNotification(
            Long userId,
            Notification.NotificationType type,
            String title,
            String message,
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

        // WebSocket으로 실시간 알림 전송
        try {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + userId,
                    (Object) dto);
            System.out.println("알림 전송 성공: userId=" + userId + ", type=" + type);
        } catch (Exception e) {
            System.err.println("알림 전송 실패: " + e.getMessage());
        }

    }

    // 사용자 알림 목록 조회
    @Transactional(readOnly = true)
    public List<NotificationDTO.Response> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 읽지 않은 알림 개수
    @Transactional(readOnly = true)
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // 알림 읽음 처리
    @Transactional
    @SuppressWarnings("null")
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    // 알림 삭제
    @Transactional
    @SuppressWarnings("null")
    public void deleteNotification(Long notificationId) {
        notificationRepository.deleteById(notificationId);
    }
}