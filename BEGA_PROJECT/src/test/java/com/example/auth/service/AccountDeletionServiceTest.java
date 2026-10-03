package com.example.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.AccountDeletionToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @InjectMocks
    private AccountDeletionService accountDeletionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @Mock
    private PasswordEncoder bCryptPasswordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AccountSecurityService accountSecurityService;

    @Mock
    private AccountDeletionFinalizationService accountDeletionFinalizationService;

    @Test
    void scheduleAccountDeletion_sendsRecoveryEmailWithAccountSettingsRedirect() {
        UserEntity user = UserEntity.builder()
                .id(41L)
                .uniqueId(UUID.randomUUID())
                .email("user@example.com")
                .role("ROLE_USER")
                .name("user")
                .handle("@user")
                .password("encoded-password")
                .provider("LOCAL")
                .enabled(true)
                .build();

        when(userRepository.findByIdForWrite(41L)).thenReturn(Optional.of(user));
        when(bCryptPasswordEncoder.matches("Password1!", "encoded-password")).thenReturn(true);
        when(accountDeletionTokenRepository.save(any(AccountDeletionToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        accountDeletionService.scheduleAccountDeletion(41L, "Password1!");

        verify(refreshRepository).deleteByEmail("user@example.com");
        verify(accountDeletionTokenRepository).deleteByUser_Id(41L);
        verify(emailService).sendAccountDeletionRecoveryEmail(
                eq("user@example.com"),
                anyString(),
                any(),
                eq("/mypage?view=accountSettings"));
        verify(accountSecurityService).recordAccountDeletionScheduled(anyLong(), any());
    }

    @Test
    void scheduleAccountDeletionRejectsAdministrativelyDisabledUser() {
        UserEntity user = UserEntity.builder()
                .id(42L)
                .email("disabled@example.com")
                .enabled(false)
                .provider("LOCAL")
                .password("encoded-password")
                .build();
        when(userRepository.findByIdForWrite(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountDeletionService.scheduleAccountDeletion(42L, "Password1!"))
                .hasMessageContaining("비활성화");

        verify(accountDeletionTokenRepository, never()).save(any());
        verify(emailService, never()).sendAccountDeletionRecoveryEmail(any(), any(), any(), any());
    }

    @Test
    void recoverAccountLocksUserAndRejectsClearedAdminDeletionState() {
        UserEntity tokenUser = UserEntity.builder()
                .id(43L)
                .enabled(false)
                .pendingDeletion(true)
                .deletionScheduledFor(java.time.LocalDateTime.now().plusDays(3))
                .build();
        UserEntity lockedUser = UserEntity.builder()
                .id(43L)
                .enabled(false)
                .pendingDeletion(false)
                .build();
        AccountDeletionToken token = AccountDeletionToken.builder()
                .token("recovery-token")
                .user(tokenUser)
                .expiryDate(java.time.LocalDateTime.now().plusDays(3))
                .build();
        when(accountDeletionTokenRepository.findUserIdByToken("recovery-token"))
                .thenReturn(Optional.of(43L));
        when(userRepository.findByIdForWrite(43L)).thenReturn(Optional.of(lockedUser));
        when(accountDeletionTokenRepository.findByTokenAndUser_Id("recovery-token", 43L))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> accountDeletionService.recoverAccount("recovery-token"))
                .hasMessageContaining("복구 가능한");

        verify(userRepository).findByIdForWrite(43L);
        verify(userRepository, never()).save(any());
        assertThat(lockedUser.isEnabled()).isFalse();
    }

    @Test
    void recoverAccountReloadsTokenAfterLockingUser() {
        UserEntity lockedUser = UserEntity.builder()
                .id(44L)
                .enabled(false)
                .pendingDeletion(true)
                .deletionScheduledFor(java.time.LocalDateTime.now().plusDays(2))
                .build();
        when(accountDeletionTokenRepository.findUserIdByToken("recovery-token"))
                .thenReturn(Optional.of(44L));
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(lockedUser));
        when(accountDeletionTokenRepository.findByTokenAndUser_Id("recovery-token", 44L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountDeletionService.recoverAccount("recovery-token"))
                .hasMessageContaining("유효하지 않은 복구 링크");

        InOrder lockBeforeTokenRecheck = inOrder(userRepository, accountDeletionTokenRepository);
        lockBeforeTokenRecheck.verify(accountDeletionTokenRepository).findUserIdByToken("recovery-token");
        lockBeforeTokenRecheck.verify(userRepository).findByIdForWrite(44L);
        lockBeforeTokenRecheck.verify(accountDeletionTokenRepository)
                .findByTokenAndUser_Id("recovery-token", 44L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void recoverAccountRechecksDeletionDeadlineAfterLockingUser() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        UserEntity lockedUser = UserEntity.builder()
                .id(47L)
                .enabled(false)
                .pendingDeletion(true)
                .deletionScheduledFor(now.minusSeconds(1))
                .build();
        AccountDeletionToken currentToken = AccountDeletionToken.builder()
                .token("recovery-token")
                .user(lockedUser)
                .expiryDate(now.plusDays(1))
                .build();
        when(accountDeletionTokenRepository.findUserIdByToken("recovery-token"))
                .thenReturn(Optional.of(47L));
        when(userRepository.findByIdForWrite(47L)).thenReturn(Optional.of(lockedUser));
        when(accountDeletionTokenRepository.findByTokenAndUser_Id("recovery-token", 47L))
                .thenReturn(Optional.of(currentToken));

        assertThatThrownBy(() -> accountDeletionService.recoverAccount("recovery-token"))
                .hasMessageContaining("복구 가능한 계정 삭제 예약");

        verify(userRepository, never()).save(any());
        verify(accountSecurityService, never()).recordAccountDeletionCancelled(anyLong());
    }

    @Test
    void finalizeDueDeletionsContinuesAfterOneUserFails() {
        when(userRepository.findDuePendingDeletionUserIds(any()))
                .thenReturn(java.util.List.of(44L, 45L, 46L));
        when(accountDeletionFinalizationService.finalizeIfDue(eq(44L), any())).thenReturn(true);
        when(accountDeletionFinalizationService.finalizeIfDue(eq(45L), any()))
                .thenThrow(new IllegalStateException("synthetic per-user failure"));
        when(accountDeletionFinalizationService.finalizeIfDue(eq(46L), any())).thenReturn(true);

        accountDeletionService.finalizeDueDeletions();

        verify(accountDeletionFinalizationService).finalizeIfDue(eq(44L), any());
        verify(accountDeletionFinalizationService).finalizeIfDue(eq(45L), any());
        verify(accountDeletionFinalizationService).finalizeIfDue(eq(46L), any());
    }
}
