package com.example.admin.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserDeletionPreparationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @InjectMocks
    private AdminUserDeletionPreparationService preparationService;

    @Test
    void disableForDeletionLocksAndDisablesActiveUser() {
        UserEntity user = UserEntity.builder()
                .id(41L)
                .enabled(true)
                .tokenVersion(3)
                .lockExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        given(userRepository.findByIdForWrite(41L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UserEntity result = preparationService.disableForDeletion(41L);

        assertThat(result).isSameAs(user);
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getTokenVersion()).isEqualTo(4);
        assertThat(user.getLockExpiresAt()).isNull();
        assertThat(user.isPendingDeletion()).isFalse();
        verify(userRepository).findByIdForWrite(41L);
        verify(userRepository).save(user);
        verify(accountDeletionTokenRepository).deleteByUser_Id(41L);
    }

    @Test
    void disableForDeletionDoesNotIncrementTokenVersionAgainOnRetry() {
        UserEntity user = UserEntity.builder()
                .id(42L)
                .enabled(false)
                .tokenVersion(7)
                .build();
        given(userRepository.findByIdForWrite(42L)).willReturn(Optional.of(user));

        preparationService.disableForDeletion(42L);

        assertThat(user.getTokenVersion()).isEqualTo(7);
        verify(userRepository, never()).save(user);
        verify(accountDeletionTokenRepository).deleteByUser_Id(42L);
    }

    @Test
    void disableForDeletionClearsRecoveryStateEvenWhenAlreadyDisabled() {
        UserEntity user = UserEntity.builder()
                .id(43L)
                .enabled(false)
                .tokenVersion(8)
                .pendingDeletion(true)
                .deletionRequestedAt(LocalDateTime.now().minusDays(1))
                .deletionScheduledFor(LocalDateTime.now().plusDays(6))
                .build();
        given(userRepository.findByIdForWrite(43L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        preparationService.disableForDeletion(43L);

        assertThat(user.getTokenVersion()).isEqualTo(8);
        assertThat(user.isPendingDeletion()).isFalse();
        assertThat(user.getDeletionRequestedAt()).isNull();
        assertThat(user.getDeletionScheduledFor()).isNull();
        verify(userRepository).save(user);
        verify(accountDeletionTokenRepository).deleteByUser_Id(43L);
    }

    @Test
    void disableForDeletionUsesRequiresNewTransaction() throws Exception {
        Method method = AdminUserDeletionPreparationService.class
                .getMethod("disableForDeletion", Long.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
