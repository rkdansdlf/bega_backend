package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.Column;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.notification.dto.NotificationDTO;
import com.example.notification.entity.Notification;
import com.example.notification.exception.NotificationNotFoundException;
import com.example.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void notificationTypeNamesFitDatabaseColumn() throws NoSuchFieldException {
        Column typeColumn = Notification.class.getDeclaredField("type").getAnnotation(Column.class);
        int maxEnumNameLength = Arrays.stream(Notification.NotificationType.values())
                .map(Enum::name)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        assertThat(maxEnumNameLength).isLessThanOrEqualTo(typeColumn.length());
    }

    // --- getMyNotifications ---

    @Test
    void getMyNotifications_returnsNotificationList() {
        Notification n1 = buildNotification(1L, 10L, Notification.NotificationType.APPLICATION_RECEIVED, "제목1", "메시지1");
        Notification n2 = buildNotification(2L, 10L, Notification.NotificationType.POST_COMMENT, "제목2", "메시지2");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n1, n2)));

        List<NotificationDTO.Response> result = notificationService.getMyNotifications(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("제목1");
        assertThat(result.get(1).getType()).isEqualTo(Notification.NotificationType.POST_COMMENT);
    }

    @Test
    void getMyNotifications_defaultsToFirstPageOf30() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.getMyNotifications(10L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(10L), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isZero();
        assertThat(captured.getPageSize()).isEqualTo(NotificationService.DEFAULT_NOTIFICATION_PAGE_SIZE);
    }

    @Test
    void getMyNotifications_passesThroughCustomPageable() {
        Pageable custom = PageRequest.of(2, 10);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(10L), eq(custom)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.getMyNotifications(10L, custom);

        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(10L, custom);
    }

    @Test
    void getMyNotifications_throwsWhenUserIdNull() {
        assertThatThrownBy(() -> notificationService.getMyNotifications(null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void getMyNotifications_throwsWhenUserIdZeroOrNegative() {
        assertThatThrownBy(() -> notificationService.getMyNotifications(0L))
                .isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> notificationService.getMyNotifications(-1L))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    // --- getMyUnreadCount ---

    @Test
    void getMyUnreadCount_returnsCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse(10L)).thenReturn(3L);

        Long count = notificationService.getMyUnreadCount(10L);

        assertThat(count).isEqualTo(3L);
    }

    // --- markAsRead ---

    @Test
    void markAsRead_setsIsReadTrue() {
        Notification notification = buildNotification(1L, 10L, Notification.NotificationType.NEW_FOLLOWER, "팔로워", "메시지");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 10L);

        assertThat(notification.getIsRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_throwsWhenNotFound() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L, 10L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markAsRead_throwsWhenNotOwner() {
        Notification notification = buildNotification(1L, 10L, Notification.NotificationType.NEW_FOLLOWER, "팔로워", "메시지");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 999L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // --- deleteNotification ---

    @Test
    void deleteNotification_deletesSuccessfully() {
        Notification notification = buildNotification(1L, 10L, Notification.NotificationType.PARTY_EXPIRED, "만료", "메시지");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.deleteNotification(1L, 10L);

        verify(notificationRepository).delete(notification);
    }

    @Test
    void deleteNotification_throwsWhenNotFound() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteNotification(99L, 10L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void deleteNotification_throwsWhenNotOwner() {
        Notification notification = buildNotification(1L, 10L, Notification.NotificationType.PARTY_EXPIRED, "만료", "메시지");
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.deleteNotification(1L, 999L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // --- createNotification ---

    @Test
    void createNotification_savesNotification() {
        // TransactionSynchronizationManager needs an active synchronization context
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        return Notification.builder()
                                .id(1L)
                                .userId(n.getUserId())
                                .type(n.getType())
                                .title(n.getTitle())
                                .message(n.getMessage())
                                .relatedId(n.getRelatedId())
                                .isRead(false)
                                .build();
                    });

            notificationService.createNotification(
                    10L,
                    Notification.NotificationType.APPLICATION_APPROVED,
                    "승인됨",
                    "신청이 승인되었습니다.",
                    100L);

            verify(notificationRepository).save(any(Notification.class));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // --- helpers ---

    private Notification buildNotification(Long id, Long userId, Notification.NotificationType type, String title, String message) {
        Notification notification = Notification.builder()
                .id(id)
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .relatedId(100L)
                .isRead(false)
                .build();
        // createdAt is set by @PrePersist, set manually for test
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }
}
