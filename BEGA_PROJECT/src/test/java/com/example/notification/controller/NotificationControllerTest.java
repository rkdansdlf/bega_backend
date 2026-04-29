package com.example.notification.controller;

import com.example.notification.dto.NotificationDTO;
import com.example.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    @Test
    @DisplayName("내 알림 목록을 조회한다")
    void getMyNotifications_returnsList() {
        NotificationDTO.Response resp = NotificationDTO.Response.builder().id(1L).build();
        Pageable pageable = PageRequest.of(0, 30);
        when(notificationService.getMyNotifications(eq(42L), any(Pageable.class))).thenReturn(List.of(resp));

        ResponseEntity<List<NotificationDTO.Response>> result = controller.getMyNotifications(42L, pageable);

        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("읽지 않은 알림 개수를 조회한다")
    void getMyUnreadCount_returnsCount() {
        when(notificationService.getMyUnreadCount(42L)).thenReturn(5L);

        ResponseEntity<Long> result = controller.getMyUnreadCount(42L);

        assertThat(result.getBody()).isEqualTo(5L);
    }

    @Test
    @DisplayName("알림을 읽음 처리한다")
    void markAsRead_returns204() {
        ResponseEntity<?> result = controller.markAsRead(1L, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(notificationService).markAsRead(1L, 42L);
    }

    @Test
    @DisplayName("알림을 삭제한다")
    void deleteNotification_returns204() {
        ResponseEntity<?> result = controller.deleteNotification(1L, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(notificationService).deleteNotification(1L, 42L);
    }
}
