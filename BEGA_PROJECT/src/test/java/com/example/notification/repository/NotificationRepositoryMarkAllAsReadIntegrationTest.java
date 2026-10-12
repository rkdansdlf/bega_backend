package com.example.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.example.notification.entity.Notification;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_read_boundary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class NotificationRepositoryMarkAllAsReadIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void markAllAsReadUpdatesOnlyUnreadNotificationsAtOrBeforeTheBoundary() {
        LocalDateTime readThrough = LocalDateTime.parse("2026-10-12T11:30:00.123456");
        Long beforeId = saveNotification(10L, readThrough.minusNanos(1_000), false);
        Long boundaryId = saveNotification(10L, readThrough, false);
        Long afterId = saveNotification(10L, readThrough.plusNanos(1_000), false);
        Long otherUserId = saveNotification(11L, readThrough.minusMinutes(1), false);

        entityManager.flush();
        entityManager.clear();
        assertThat(notificationRepository.findById(beforeId).orElseThrow().getCreatedAt())
                .isEqualTo(readThrough.minusNanos(1_000));

        notificationRepository.markAllAsReadByUserId(10L, readThrough);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findById(beforeId).orElseThrow().getIsRead()).isTrue();
        assertThat(notificationRepository.findById(boundaryId).orElseThrow().getIsRead()).isTrue();
        assertThat(notificationRepository.findById(afterId).orElseThrow().getIsRead()).isFalse();
        assertThat(notificationRepository.findById(otherUserId).orElseThrow().getIsRead()).isFalse();
    }

    private Long saveNotification(Long userId, LocalDateTime createdAt, boolean isRead) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.NEW_FOLLOWER)
                .title("테스트 알림")
                .message("저장소 경계 테스트")
                .isRead(isRead)
                .build();
        notificationRepository.saveAndFlush(notification);
        entityManager.createNativeQuery("UPDATE notifications SET createdat = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", notification.getId())
                .executeUpdate();
        return notification.getId();
    }
}
