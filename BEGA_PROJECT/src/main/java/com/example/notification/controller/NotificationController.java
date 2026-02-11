package com.example.notification.controller;

import com.example.notification.dto.NotificationDTO;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 내 알림 목록 조회
    @GetMapping("/my")
    public ResponseEntity<List<NotificationDTO.Response>> getMyNotifications(java.security.Principal principal) {
        List<NotificationDTO.Response> notifications = notificationService.getMyNotifications(principal);
        return ResponseEntity.ok(notifications);
    }

    // 내 읽지 않은 알림 개수
    @GetMapping("/my/unread-count")
    public ResponseEntity<Long> getMyUnreadCount(java.security.Principal principal) {
        Long count = notificationService.getMyUnreadCount(principal);
        return ResponseEntity.ok(count);
    }

    // 알림 읽음 처리
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId, java.security.Principal principal) {
        try {
            notificationService.markAsRead(notificationId, principal);
            return ResponseEntity.noContent().build();
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 알림 삭제
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long notificationId, java.security.Principal principal) {
        try {
            notificationService.deleteNotification(notificationId, principal);
            return ResponseEntity.noContent().build();
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }
}