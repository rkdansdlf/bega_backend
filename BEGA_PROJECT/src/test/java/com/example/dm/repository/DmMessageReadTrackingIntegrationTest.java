package com.example.dm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.common.realtime.RealtimeOutboxWriter;
import com.example.dm.dto.DmMessageDto;
import com.example.dm.entity.DmMessage;
import com.example.dm.entity.DmRoom;
import com.example.dm.service.DmMessageService;
import com.example.dm.service.DmRoomService;

@DataJpaTest
@Import(DmMessageService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dm_read_tracking;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DmMessageReadTrackingIntegrationTest {

    private static final Long PARTICIPANT_ONE = 10L;
    private static final Long PARTICIPANT_TWO = 20L;
    private static final long TEST_OPERATION_TIMEOUT_SECONDS = 10;
    private static final long LATCH_TIMEOUT_SECONDS = 15;
    private static final long LOCK_WAIT_DETECTION_TIMEOUT_SECONDS = 4;

    @Autowired
    private DmRoomRepository dmRoomRepository;

    @Autowired
    private DmMessageRepository dmMessageRepository;

    @Autowired
    private DmMessageService dmMessageService;

    @MockitoBean
    private DmRoomService dmRoomService;

    @MockitoBean
    private RealtimeOutboxWriter realtimeOutboxWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;
    private Long roomId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        roomId = transactionTemplate.execute(status -> {
            DmRoom room = dmRoomRepository.saveAndFlush(DmRoom.builder()
                    .participantOneId(PARTICIPANT_ONE)
                    .participantTwoId(PARTICIPANT_TWO)
                    .build());
            return room.getId();
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> dmRoomRepository.deleteAll());
    }

    @Test
    @DisplayName("message ID watermark takes precedence over the legacy timestamp")
    void unreadRooms_useMessageIdWatermarkWhenPresent() {
        List<Long> unreadRooms = transactionTemplate.execute(status -> {
            DmMessage alreadyRead = dmMessageRepository.saveAndFlush(message(PARTICIPANT_TWO));
            DmRoom room = dmRoomRepository.findById(roomId).orElseThrow();
            room.setParticipantOneLastReadMessageId(alreadyRead.getId());
            room.setParticipantOneLastReadAt(Instant.parse("2099-01-01T00:00:00Z"));
            dmRoomRepository.save(room);

            DmMessage unread = dmMessageRepository.save(message(PARTICIPANT_TWO));
            dmMessageRepository.save(message(PARTICIPANT_ONE));
            dmMessageRepository.flush();
            assertThat(unread.getId()).isGreaterThan(alreadyRead.getId());

            return dmMessageRepository.findUnreadRoomIdsForParticipant(PARTICIPANT_ONE, List.of(roomId));
        });

        assertThat(unreadRooms).containsExactly(roomId);
    }

    @Test
    @DisplayName("rooms without an ID watermark retain timestamp-based unread compatibility")
    void unreadRooms_fallsBackToLegacyTimestamp() {
        List<Long> unreadRooms = transactionTemplate.execute(status -> {
            DmRoom room = dmRoomRepository.findById(roomId).orElseThrow();
            room.setParticipantOneLastReadAt(Instant.now().plusSeconds(60));
            dmRoomRepository.save(room);

            dmMessageRepository.save(message(PARTICIPANT_TWO));
            dmMessageRepository.save(message(PARTICIPANT_TWO));
            dmMessageRepository.flush();
            assertThat(dmMessageRepository.findUnreadRoomIdsForParticipant(PARTICIPANT_ONE, List.of(roomId))).isEmpty();

            room.setParticipantOneLastReadAt(Instant.now().minusSeconds(60));
            dmRoomRepository.save(room);
            dmMessageRepository.save(message(PARTICIPANT_ONE));

            return dmMessageRepository.findUnreadRoomIdsForParticipant(PARTICIPANT_ONE, List.of(roomId));
        });

        assertThat(unreadRooms).containsExactly(roomId);
    }

    @Test
    @DisplayName("room lock serializes history watermark updates with message sends")
    void roomLock_preventsMessagesFromArrivingBetweenHistoryAndReadMarker() throws Exception {
        Long existingMessageId = transactionTemplate.execute(status -> dmMessageRepository
                .saveAndFlush(message(PARTICIPANT_TWO))
                .getId());
        CountDownLatch readerLockedRoom = new CountDownLatch(1);
        CountDownLatch allowReaderToCommit = new CountDownLatch(1);
        CountDownLatch senderStarted = new CountDownLatch(1);
        AtomicInteger senderSessionId = new AtomicInteger(-1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> reader = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                DmRoom room = dmRoomRepository
                        .findAccessibleByIdAndParticipantIdForUpdate(roomId, PARTICIPANT_ONE)
                        .orElseThrow();
                List<DmMessage> history = dmMessageRepository.findByRoomIdOrderByCreatedAtAscIdAsc(roomId);
                Long watermark = history.stream().map(DmMessage::getId).max(Long::compareTo).orElseThrow();
                room.setParticipantOneLastReadMessageId(watermark);
                room.setParticipantOneLastReadAt(history.get(history.size() - 1).getCreatedAt());
                dmRoomRepository.save(room);
                readerLockedRoom.countDown();
                await(allowReaderToCommit);
            }));

            assertThat(readerLockedRoom.await(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<?> sender = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                senderSessionId.set(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class));
                senderStarted.countDown();
                dmRoomRepository.findAccessibleByIdAndParticipantIdForUpdate(roomId, PARTICIPANT_TWO)
                        .orElseThrow();
                dmMessageRepository.saveAndFlush(message(PARTICIPANT_TWO));
            }));

            assertThat(senderStarted.await(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLockWait(senderSessionId.get());
            assertThat(sender.isDone()).isFalse();

            allowReaderToCommit.countDown();
            reader.get(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sender.get(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            allowReaderToCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(existingMessageId).isNotNull();
        assertThat(dmMessageRepository.findUnreadRoomIdsForParticipant(PARTICIPANT_ONE, List.of(roomId)))
                .containsExactly(roomId);
    }

    @Test
    @DisplayName("DM service transaction keeps the room lock through history and read-watermark update")
    void getMessages_holdsRoomLockUntilHistoryWatermarkCommits() throws Exception {
        Long existingMessageId = transactionTemplate.execute(status -> dmMessageRepository
                .saveAndFlush(message(PARTICIPANT_TWO))
                .getId());
        CountDownLatch readerLockedRoom = new CountDownLatch(1);
        CountDownLatch allowReaderToContinue = new CountDownLatch(1);
        CountDownLatch senderStarted = new CountDownLatch(1);
        AtomicInteger senderSessionId = new AtomicInteger(-1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        doAnswer(invocation -> {
            DmRoom lockedRoom = dmRoomRepository
                    .findAccessibleByIdAndParticipantIdForUpdate(roomId, PARTICIPANT_ONE)
                    .orElseThrow();
            readerLockedRoom.countDown();
            await(allowReaderToContinue);
            return lockedRoom;
        }).when(dmRoomService).getAccessibleRoomForUpdate(roomId, PARTICIPANT_ONE);
        doAnswer(invocation -> {
            DmRoom lockedRoom = invocation.getArgument(0);
            Long messageId = invocation.getArgument(2);
            lockedRoom.setParticipantOneLastReadMessageId(messageId);
            dmRoomRepository.save(lockedRoom);
            return null;
        }).when(dmRoomService).markAsReadThrough(
                any(DmRoom.class), eq(PARTICIPANT_ONE), any(Long.class), any());

        try {
            Future<?> reader = executor.submit(() -> dmMessageService.getMessages(roomId, PARTICIPANT_ONE));

            assertThat(readerLockedRoom.await(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<?> sender = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                senderSessionId.set(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class));
                senderStarted.countDown();
                dmRoomRepository.findAccessibleByIdAndParticipantIdForUpdate(roomId, PARTICIPANT_TWO)
                        .orElseThrow();
                dmMessageRepository.saveAndFlush(message(PARTICIPANT_TWO));
            }));

            assertThat(senderStarted.await(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLockWait(senderSessionId.get());
            assertThat(sender.isDone()).isFalse();

            allowReaderToContinue.countDown();
            reader.get(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sender.get(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            allowReaderToContinue.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(existingMessageId).isNotNull();
        assertThat(dmRoomRepository.findById(roomId).orElseThrow().getParticipantOneLastReadMessageId())
                .isEqualTo(existingMessageId);
        assertThat(dmMessageRepository.findUnreadRoomIdsForParticipant(PARTICIPANT_ONE, List.of(roomId)))
                .containsExactly(roomId);
    }

    @Test
    @DisplayName("DM send transaction keeps the room lock until the message commits")
    void sendMessage_holdsRoomLockUntilMessageCommits() throws Exception {
        CountDownLatch messageReachedOutbox = new CountDownLatch(1);
        CountDownLatch allowSenderToCommit = new CountDownLatch(1);
        CountDownLatch readerStarted = new CountDownLatch(1);
        AtomicInteger readerSessionId = new AtomicInteger(-1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        doAnswer(invocation -> dmRoomRepository
                .findAccessibleByIdAndParticipantIdForUpdate(roomId, PARTICIPANT_TWO)
                .orElseThrow())
                .when(dmRoomService).getAccessibleRoomForUpdate(roomId, PARTICIPANT_TWO);
        doAnswer(invocation -> {
            messageReachedOutbox.countDown();
            await(allowSenderToCommit);
            return null;
        }).when(realtimeOutboxWriter).broadcast(eq("/topic/dm/" + roomId), any());

        try {
            Future<?> sender = executor.submit(() -> dmMessageService.sendMessage(
                    PARTICIPANT_TWO,
                    DmMessageDto.Request.builder()
                            .roomId(roomId)
                            .content("새 메시지")
                            .build()));

            assertThat(messageReachedOutbox.await(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<?> reader = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                readerSessionId.set(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class));
                readerStarted.countDown();
                dmRoomRepository.findAccessibleByIdAndParticipantIdForUpdate(roomId, PARTICIPANT_ONE)
                        .orElseThrow();
            }));

            assertThat(readerStarted.await(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLockWait(readerSessionId.get());
            assertThat(reader.isDone()).isFalse();

            allowSenderToCommit.countDown();
            sender.get(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            reader.get(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            allowSenderToCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TEST_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        List<DmMessage> messages = dmMessageRepository.findByRoomIdOrderByCreatedAtAscIdAsc(roomId);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSenderId()).isEqualTo(PARTICIPANT_TWO);
        assertThat(messages.get(0).getContent()).isEqualTo("새 메시지");
    }

    private DmMessage message(Long senderId) {
        return DmMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("test")
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release DM room lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release DM room lock", exception);
        }
    }

    private void awaitDatabaseLockWait(int sessionId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LOCK_WAIT_DETECTION_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer blockedSessionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.SESSIONS
                    WHERE SESSION_ID = ? AND BLOCKER_ID IS NOT NULL
                    """, Integer.class, sessionId);
            if (blockedSessionCount != null && blockedSessionCount > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Sender session never reported waiting on the room lock");
    }
}
