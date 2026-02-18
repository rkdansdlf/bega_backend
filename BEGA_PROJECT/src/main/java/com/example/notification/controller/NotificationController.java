package com.example.notification.controller;

import com.example.notification.dto.NotificationDTO;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 내 알림 목록 조회
    @GetMapping("/my")
    public ResponseEntity<List<NotificationDTO.Response>> getMyNotifications(@AuthenticationPrincipal Long userId) {
        List<NotificationDTO.Response> notifications = notificationService.getMyNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    // 내 읽지 않은 알림 개수
    @GetMapping("/my/unread-count")
    public ResponseEntity<Long> getMyUnreadCount(@AuthenticationPrincipal Long userId) {
        Long count = notificationService.getMyUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<Long> getUnreadCountByUserId(
            @PathVariable Long userId,
            @AuthenticationPrincipal Long principalUserId) {
        Long count = notificationService.getUnreadCountByUserId(userId, principalUserId);
        return ResponseEntity.ok(count);
    }

    // 알림 읽음 처리
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId, @AuthenticationPrincipal Long userId) {
        try {
            notificationService.markAsRead(notificationId, userId);
            return ResponseEntity.noContent().build();
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 알림 삭제
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long notificationId, @AuthenticationPrincipal Long userId) {
        try {
            notificationService.deleteNotification(notificationId, userId);
            return ResponseEntity.noContent().build();
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
