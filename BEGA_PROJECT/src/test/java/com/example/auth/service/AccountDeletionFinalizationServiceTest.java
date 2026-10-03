package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.service.PartyService;
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
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @Mock
    private PartyService partyService;

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
        InOrder finalizationOrder = inOrder(userRepository, partyService, accountDeletionTokenRepository);
        finalizationOrder.verify(userRepository).findByIdForWrite(44L);
        finalizationOrder.verify(partyService).handleUserDeletion(44L);
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
        verify(userRepository, never()).save(any());
        verify(accountDeletionTokenRepository, never()).deleteByUser_Id(44L);
    }

    @Test
    void processesEachAccountInItsOwnNewTransaction() throws NoSuchMethodException {
        Transactional transactional = AccountDeletionFinalizationService.class
                .getMethod("finalizeIfDue", Long.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
