package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.service.PartyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
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
    private EntityManager entityManager;

    @Mock
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @Mock
    private PartyService partyService;

    @Mock
    private AccountDeletionIdentityService accountDeletionIdentityService;

    @Mock
    private AccountDeletionCommunicationService accountDeletionCommunicationService;

    @Mock
    private AccountDeletionContentService accountDeletionContentService;

    @Mock
    private AccountDeletionReviewService accountDeletionReviewService;

    @Mock
    private AccountDeletionFinancialService accountDeletionFinancialService;

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
                accountDeletionCommunicationService,
                accountDeletionContentService,
                accountDeletionReviewService,
                accountDeletionFinancialService,
                accountDeletionIdentityService,
                accountDeletionTokenRepository);
        finalizationOrder.verify(userRepository).findByIdForWrite(44L);
        finalizationOrder.verify(partyService).handleUserDeletion(44L);
        finalizationOrder.verify(accountDeletionCommunicationService).anonymize(44L);
        finalizationOrder.verify(accountDeletionContentService).anonymize(44L);
        finalizationOrder.verify(accountDeletionReviewService).anonymize(44L);
        finalizationOrder.verify(accountDeletionFinancialService).anonymize(44L);
        finalizationOrder.verify(accountDeletionIdentityService).anonymize(user);
        finalizationOrder.verify(userRepository).save(user);
        finalizationOrder.verify(accountDeletionTokenRepository).deleteByUser_Id(44L);
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
        verify(accountDeletionCommunicationService, never()).anonymize(44L);
        verify(accountDeletionContentService, never()).anonymize(44L);
        verify(accountDeletionReviewService, never()).anonymize(44L);
        verify(accountDeletionFinancialService, never()).anonymize(44L);
        verify(accountDeletionIdentityService, never()).anonymize(any());
        verify(userRepository, never()).save(any());
        verify(accountDeletionTokenRepository, never()).deleteByUser_Id(44L);
    }

    @Test
    void immediatelyAnonymizesAdminDeletedUserWithoutDuplicatingPartyCleanup() {
        UserEntity user = UserEntity.builder()
                .id(44L)
                .role("ROLE_USER")
                .enabled(false)
                .pendingDeletion(true)
                .deletionScheduledFor(LocalDateTime.now())
                .build();
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(user));

        boolean finalized = finalizationService.finalizeImmediatelyAfterAdminDeletion(44L);

        assertThat(finalized).isTrue();
        InOrder finalizationOrder = inOrder(
                userRepository,
                entityManager,
                partyService,
                accountDeletionCommunicationService,
                accountDeletionContentService,
                accountDeletionReviewService,
                accountDeletionFinancialService,
                accountDeletionIdentityService,
                accountDeletionTokenRepository);
        finalizationOrder.verify(userRepository).findByIdForWrite(44L);
        finalizationOrder.verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        finalizationOrder.verify(partyService).handleUserDeletion(44L);
        finalizationOrder.verify(accountDeletionCommunicationService).anonymize(44L);
        finalizationOrder.verify(accountDeletionContentService).anonymize(44L);
        finalizationOrder.verify(accountDeletionReviewService).anonymize(44L);
        finalizationOrder.verify(accountDeletionFinancialService).anonymize(44L);
        finalizationOrder.verify(accountDeletionIdentityService).anonymize(user);
        finalizationOrder.verify(userRepository).save(user);
        finalizationOrder.verify(accountDeletionTokenRepository).deleteByUser_Id(44L);
    }

    @Test
    void treatsAnAlreadyAnonymizedTombstoneAsAnIdempotentNoOp() {
        UserEntity staleUser = UserEntity.builder()
                .id(44L)
                .role("ROLE_USER")
                .enabled(false)
                .name("Former name")
                .email("former@example.test")
                .build();
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(staleUser));
        doAnswer(invocation -> {
            UserEntity refreshedUser = invocation.getArgument(0);
            refreshedUser.setName("탈퇴한 사용자");
            refreshedUser.setEmail("deleted-44@deleted.invalid");
            refreshedUser.setPendingDeletion(true);
            refreshedUser.setLocked(true);
            return null;
        }).when(entityManager).refresh(staleUser, LockModeType.PESSIMISTIC_WRITE);

        boolean finalized = finalizationService.finalizeImmediatelyAfterAdminDeletion(44L);

        assertThat(finalized).isFalse();
        verify(entityManager).refresh(staleUser, LockModeType.PESSIMISTIC_WRITE);
        verify(partyService, never()).handleUserDeletion(44L);
        verify(accountDeletionCommunicationService, never()).anonymize(44L);
        verify(accountDeletionContentService, never()).anonymize(44L);
        verify(accountDeletionReviewService, never()).anonymize(44L);
        verify(accountDeletionFinancialService, never()).anonymize(44L);
        verify(accountDeletionIdentityService, never()).anonymize(any());
        verify(userRepository, never()).save(any());
        verify(accountDeletionTokenRepository, never()).deleteByUser_Id(44L);
    }

    @Test
    void rejectsAdminFinalizationWithoutTheDurableRetryMarker() {
        UserEntity disabledUser = UserEntity.builder()
                .id(45L)
                .role("ROLE_USER")
                .enabled(false)
                .build();
        when(userRepository.findByIdForWrite(45L)).thenReturn(Optional.of(disabledUser));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> finalizationService.finalizeImmediatelyAfterAdminDeletion(45L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The immediate finalization retry marker is required.");

        verify(partyService, never()).handleUserDeletion(45L);
        verify(accountDeletionCommunicationService, never()).anonymize(45L);
    }

    @Test
    void processesEachAccountInItsOwnNewTransaction() throws NoSuchMethodException {
        Transactional transactional = AccountDeletionFinalizationService.class
                .getMethod("finalizeIfDue", Long.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void adminFinalizationRequiresTheAdminDeletionTransaction() throws NoSuchMethodException {
        Transactional transactional = AccountDeletionFinalizationService.class
                .getMethod("finalizeImmediatelyAfterAdminDeletion", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
