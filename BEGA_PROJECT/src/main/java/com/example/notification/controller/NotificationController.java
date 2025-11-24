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

    // 알림 목록 조회
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationDTO.Response>> getNotifications(@PathVariable Long userId) {
        List<NotificationDTO.Response> notifications = notificationService.getNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    // 읽지 않은 알림 개수
    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(@PathVariable Long userId) {
        Long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    // 알림 읽음 처리
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    // 알림 삭제
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }
}