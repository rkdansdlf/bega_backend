package com.example.notification.repository;

import com.example.notification.entity.Notification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findUnreadForBulkReadOnlySelectsCurrentUsersUnreadSnapshotIds() {
        Notification earlierByCreatedAt = notification(41L, false);
        Notification laterByCreatedAt = notification(41L, false);
        Notification notInSnapshot = notification(41L, false);
        Notification otherUsersNotification = notification(42L, false);
        Notification alreadyRead = notification(41L, true);

        notificationRepository.saveAllAndFlush(
                java.util.List.of(earlierByCreatedAt, laterByCreatedAt, notInSnapshot,
                        otherUsersNotification, alreadyRead));

        LocalDateTime arbitraryTime = LocalDateTime.of(2026, 10, 12, 11, 30, 0, 123_000_000);
        setCreatedAt(earlierByCreatedAt.getId(), arbitraryTime.minusDays(1));
        setCreatedAt(laterByCreatedAt.getId(), arbitraryTime.plusDays(1));

        java.util.List<Notification> selected = notificationRepository
                .findUnreadForUserByIdsForUpdate(41L, java.util.List.of(
                        earlierByCreatedAt.getId(), laterByCreatedAt.getId(),
                        otherUsersNotification.getId(), alreadyRead.getId()));
        selected.forEach(notification -> notification.setIsRead(true));
        entityManager.flush();
        entityManager.clear();

        Map<Long, Boolean> readStateById = notificationRepository.findAllById(
                        java.util.List.of(earlierByCreatedAt.getId(), laterByCreatedAt.getId(),
                                notInSnapshot.getId(), otherUsersNotification.getId(), alreadyRead.getId()))
                .stream()
                .collect(Collectors.toMap(Notification::getId, Notification::getIsRead));

        assertThat(selected).extracting(Notification::getId)
                .containsExactly(earlierByCreatedAt.getId(), laterByCreatedAt.getId());
        assertThat(readStateById).containsEntry(earlierByCreatedAt.getId(), true)
                .containsEntry(laterByCreatedAt.getId(), true)
                .containsEntry(notInSnapshot.getId(), false)
                .containsEntry(otherUsersNotification.getId(), false)
                .containsEntry(alreadyRead.getId(), true);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkReadSelectionCannotIncludeNotificationCommittedAfterItsSnapshot() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch notificationFlushed = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        AtomicReference<Long> notificationId = new AtomicReference<>();
        Notification snapshotNotification = notificationRepository.saveAndFlush(notification(43L, false));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        try {
            Future<Void> notificationCommit = executor.submit(() -> transaction.execute(status -> {
                Notification notification = notification(43L, false);
                Notification saved = notificationRepository.saveAndFlush(notification);
                notificationId.set(saved.getId());
                notificationFlushed.countDown();
                await(allowCommit);
                return null;
            }));

            assertThat(notificationFlushed.await(10, TimeUnit.SECONDS)).isTrue();
            java.util.List<Long> selectedIds = transaction.execute(status -> {
                java.util.List<Notification> selected = notificationRepository
                        .findUnreadForUserByIdsForUpdate(43L, java.util.List.of(snapshotNotification.getId()));
                selected.forEach(notification -> notification.setIsRead(true));
                return selected.stream().map(Notification::getId).toList();
            });

            allowCommit.countDown();
            notificationCommit.get(10, TimeUnit.SECONDS);

            Notification committed = notificationRepository.findById(notificationId.get()).orElseThrow();
            Notification snapshotCommitted = notificationRepository.findById(snapshotNotification.getId()).orElseThrow();
            assertThat(selectedIds).containsExactly(snapshotNotification.getId());
            assertThat(committed.getIsRead()).isFalse();
            assertThat(snapshotCommitted.getIsRead()).isTrue();
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            if (notificationId.get() != null) {
                notificationRepository.deleteById(notificationId.get());
            }
            notificationRepository.deleteById(snapshotNotification.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentBulkReadsReportAnUnreadSnapshotIdOnlyOnce() throws Exception {
        Notification snapshotNotification = notificationRepository.saveAndFlush(notification(44L, false));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstSelected = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondAttemptingQuery = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<java.util.List<Long>> firstRead = executor.submit(() -> transaction.execute(status -> {
                java.util.List<Notification> selected = notificationRepository
                        .findUnreadForUserByIdsForUpdate(44L, java.util.List.of(snapshotNotification.getId()));
                selected.forEach(notification -> notification.setIsRead(true));
                firstSelected.countDown();
                await(allowFirstCommit);
                return selected.stream().map(Notification::getId).toList();
            }));

            assertThat(firstSelected.await(10, TimeUnit.SECONDS)).isTrue();
            Future<java.util.List<Long>> secondRead = executor.submit(() -> transaction.execute(status -> {
                secondAttemptingQuery.countDown();
                java.util.List<Notification> selected = notificationRepository
                        .findUnreadForUserByIdsForUpdate(44L, java.util.List.of(snapshotNotification.getId()));
                selected.forEach(notification -> notification.setIsRead(true));
                return selected.stream().map(Notification::getId).toList();
            }));

            assertThat(secondAttemptingQuery.await(10, TimeUnit.SECONDS)).isTrue();
            boolean secondReadWaitedForTheRowLock;
            try {
                secondRead.get(100, TimeUnit.MILLISECONDS);
                secondReadWaitedForTheRowLock = false;
            } catch (TimeoutException expectedWhileFirstTransactionOwnsTheLock) {
                secondReadWaitedForTheRowLock = true;
            }
            assertThat(secondReadWaitedForTheRowLock).isTrue();

            allowFirstCommit.countDown();
            assertThat(firstRead.get(10, TimeUnit.SECONDS)).containsExactly(snapshotNotification.getId());
            assertThat(secondRead.get(10, TimeUnit.SECONDS)).isEmpty();
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
            notificationRepository.deleteById(snapshotNotification.getId());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to commit the notification");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to commit the notification", exception);
        }
    }

    private Notification notification(Long userId, boolean isRead) {
        return Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.NEW_FOLLOWER)
                .title("테스트 알림")
                .message("회귀 테스트 데이터")
                .isRead(isRead)
                .build();
    }

    private void setCreatedAt(Long id, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE notifications SET createdat = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), id);
    }
}
