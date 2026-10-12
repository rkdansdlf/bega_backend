package com.example.auth.service;

import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.mate.service.PartyService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AccountDeletionFinalizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @Mock
    private PartyService partyService;

    @Mock
    private AccountDeletionContentService contentService;

    @Mock
    private AccountDeletionFinancialService financialService;

    @Mock
    private AccountDeletionReviewService reviewService;

    @Mock
    private AccountDeletionIdentityService identityService;

    @Mock
    private CheerPostLikeRepo likeRepository;

    @Mock
    private CheerPostRepo cheerPostRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AccountDeletionFinalizationService finalizationService;

    @Test
    void finalizesOnlyAfterLockedUserStillHasDueDeletion() {
        LocalDateTime cutoff = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .id(44L)
                .pendingDeletion(true)
                .deletionScheduledFor(cutoff.minusMinutes(1))
                .build();
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        boolean finalized = finalizationService.finalizeIfDue(44L, cutoff);

        assertThat(finalized).isTrue();
        assertThat(user.getDeletionScheduledFor()).isNull();
        InOrder finalizationOrder = inOrder(
                userRepository,
                partyService,
                likeRepository,
                contentService,
                reviewService,
                financialService,
                identityService,
                accountDeletionTokenRepository);
        finalizationOrder.verify(userRepository).findByIdForWrite(44L);
        finalizationOrder.verify(partyService).handleUserDeletion(44L);
        finalizationOrder.verify(likeRepository).findByUser(user);
        finalizationOrder.verify(contentService).anonymize(44L);
        finalizationOrder.verify(reviewService).anonymize(44L);
        finalizationOrder.verify(financialService).anonymize(44L);
        finalizationOrder.verify(identityService).anonymize(user);
        finalizationOrder.verify(userRepository).save(user);
        finalizationOrder.verify(accountDeletionTokenRepository).deleteByUser_Id(44L);
        verify(auditLogRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void skipsRecoveredOrRescheduledUserAfterLockingCurrentRow() {
        LocalDateTime cutoff = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .id(44L)
                .pendingDeletion(true)
                .deletionScheduledFor(cutoff.plusSeconds(1))
                .build();
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(user));

        boolean finalized = finalizationService.finalizeIfDue(44L, cutoff);

        assertThat(finalized).isFalse();
        verify(partyService, never()).handleUserDeletion(44L);
        verify(userRepository, never()).save(any());
        verify(accountDeletionTokenRepository, never()).deleteByUser_Id(44L);
        verifyNoFinalizationSideEffects();
    }

    @Test
    void selfServiceFinalizationDoesNotRunAdminOnlyLikeCleanup() {
        LocalDateTime cutoff = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .id(44L)
                .pendingDeletion(true)
                .deletionRequestedAt(cutoff.minusDays(7))
                .deletionScheduledFor(cutoff.minusMinutes(1))
                .build();
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        boolean finalized = finalizationService.finalizeIfDue(44L, cutoff);

        assertThat(finalized).isTrue();
        verify(likeRepository, org.mockito.Mockito.never()).findByUser(any());
        verify(contentService).anonymize(44L);
        verify(reviewService).anonymize(44L);
        verify(financialService).anonymize(44L);
        verify(identityService).anonymize(user);
    }

    @Test
    void successfulAdminFinalizationWritesOnlyRedactedAuditValues() {
        LocalDateTime cutoff = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .id(44L)
                .email("private@example.test")
                .pendingDeletion(true)
                .deletionScheduledFor(cutoff.minusMinutes(1))
                .build();
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        Logger logger = (Logger) LoggerFactory.getLogger(AccountDeletionFinalizationService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        boolean finalized;
        try {
            finalized = finalizationService.finalizeImmediatelyAfterAdminDeletion(44L, 77L);
        } finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }

        assertThat(finalized).isTrue();
        verify(auditLogRepository).save(argThat((AuditLog log) -> log.getAdminId().equals(77L)
                && log.getTargetUserId().equals(44L)
                && log.getAction() == AuditLog.AuditAction.DELETE_USER
                && "[REDACTED]".equals(log.getOldValue())
                && log.getNewValue() == null));
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("private@example.test"));
    }

    @Test
    void processesEachAccountInItsOwnNewTransaction() throws NoSuchMethodException {
        Transactional transactional = AccountDeletionFinalizationService.class
                .getMethod("finalizeIfDue", Long.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private void verifyNoFinalizationSideEffects() {
        verify(likeRepository, org.mockito.Mockito.never()).findByUser(any());
        verify(contentService, org.mockito.Mockito.never()).anonymize(any());
        verify(reviewService, org.mockito.Mockito.never()).anonymize(any());
        verify(financialService, org.mockito.Mockito.never()).anonymize(any());
        verify(identityService, org.mockito.Mockito.never()).anonymize(any(UserEntity.class));
        verify(auditLogRepository, org.mockito.Mockito.never()).save(any());
    }
}
