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
import org.mockito.ArgumentCaptor;
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
    void scheduleAccountDeletion_sendsRecoveryEmailWithAccountSettingsRedirect() throws Exception {
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
        ArgumentCaptor<AccountDeletionToken> saved = ArgumentCaptor.forClass(AccountDeletionToken.class);
        ArgumentCaptor<String> emailed = ArgumentCaptor.forClass(String.class);
        verify(accountDeletionTokenRepository).save(saved.capture());
        verify(emailService).sendAccountDeletionRecoveryEmail(eq("user@example.com"), emailed.capture(), any(), any());
        assertThat(saved.getValue().getToken()).matches("[0-9a-f]{64}").isNotEqualTo(emailed.getValue());
        assertThat(saved.getValue().getToken()).isEqualTo(java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(emailed.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
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
                .token("123e4567-e89b-12d3-a456-426614174000")
                .user(tokenUser)
                .expiryDate(java.time.LocalDateTime.now().plusDays(3))
                .build();
        when(accountDeletionTokenRepository.findUserIdByToken("123e4567-e89b-12d3-a456-426614174000"))
                .thenReturn(Optional.of(43L));
        when(userRepository.findByIdForWrite(43L)).thenReturn(Optional.of(lockedUser));
        when(accountDeletionTokenRepository.findByTokenAndUser_Id("123e4567-e89b-12d3-a456-426614174000", 43L))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> accountDeletionService.recoverAccount("123e4567-e89b-12d3-a456-426614174000"))
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
        when(accountDeletionTokenRepository.findUserIdByToken("123e4567-e89b-12d3-a456-426614174000"))
                .thenReturn(Optional.of(44L));
        when(userRepository.findByIdForWrite(44L)).thenReturn(Optional.of(lockedUser));
        when(accountDeletionTokenRepository.findByTokenAndUser_Id("123e4567-e89b-12d3-a456-426614174000", 44L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountDeletionService.recoverAccount("123e4567-e89b-12d3-a456-426614174000"))
                .hasMessageContaining("유효하지 않은 복구 링크");

        InOrder lockBeforeTokenRecheck = inOrder(userRepository, accountDeletionTokenRepository);
        lockBeforeTokenRecheck.verify(accountDeletionTokenRepository).findUserIdByToken("123e4567-e89b-12d3-a456-426614174000");
        lockBeforeTokenRecheck.verify(userRepository).findByIdForWrite(44L);
        lockBeforeTokenRecheck.verify(accountDeletionTokenRepository)
                .findByTokenAndUser_Id("123e4567-e89b-12d3-a456-426614174000", 44L);
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
                .token("123e4567-e89b-12d3-a456-426614174000")
                .user(lockedUser)
                .expiryDate(now.plusDays(1))
                .build();
        when(accountDeletionTokenRepository.findUserIdByToken("123e4567-e89b-12d3-a456-426614174000"))
                .thenReturn(Optional.of(47L));
        when(userRepository.findByIdForWrite(47L)).thenReturn(Optional.of(lockedUser));
        when(accountDeletionTokenRepository.findByTokenAndUser_Id("123e4567-e89b-12d3-a456-426614174000", 47L))
                .thenReturn(Optional.of(currentToken));

        assertThatThrownBy(() -> accountDeletionService.recoverAccount("123e4567-e89b-12d3-a456-426614174000"))
                .hasMessageContaining("복구 가능한 계정 삭제 예약");

        verify(userRepository, never()).save(any());
        verify(accountSecurityService, never()).recordAccountDeletionCancelled(anyLong());
    }

    @Test
    void storedDigestCannotBeReplayedAsRecoveryToken() {
        assertThatThrownBy(() -> accountDeletionService.getRecoveryInfo("a".repeat(64)))
                .hasMessageContaining("유효하지 않은 복구 링크");
        verify(accountDeletionTokenRepository, never()).findByToken(any());
    }

    @Test
    void recoveryInfoAcceptsHashedAndLegacyTokens() throws Exception {
        String raw = "123e4567-e89b-12d3-a456-426614174000";
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        UserEntity user = UserEntity.builder().id(51L).pendingDeletion(true)
                .deletionScheduledFor(java.time.LocalDateTime.now().plusDays(1)).build();
        AccountDeletionToken hashed = AccountDeletionToken.builder().token(digest).user(user)
                .expiryDate(java.time.LocalDateTime.now().plusDays(1)).build();
        AccountDeletionToken legacy = AccountDeletionToken.builder().token(raw).user(user)
                .expiryDate(java.time.LocalDateTime.now().plusDays(1)).build();
        when(accountDeletionTokenRepository.findByToken(digest))
                .thenReturn(Optional.of(hashed), Optional.of(hashed), Optional.empty());
        when(accountDeletionTokenRepository.findByToken(raw)).thenReturn(Optional.of(legacy));
        assertThat(accountDeletionService.getRecoveryInfo(raw).getScheduledFor()).isNotNull();
        assertThat(accountDeletionService.getRecoveryInfo(raw).getScheduledFor()).isNotNull();
    }

    @Test
    void recoveryUsesDigestAfterLockAndConsumesToken() throws Exception {
        String raw = "123e4567-e89b-12d3-a456-426614174000";
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        UserEntity user = UserEntity.builder().id(52L).pendingDeletion(true).enabled(false)
                .deletionScheduledFor(java.time.LocalDateTime.now().plusDays(1)).build();
        AccountDeletionToken token = AccountDeletionToken.builder().token(digest).user(user)
                .expiryDate(java.time.LocalDateTime.now().plusDays(1)).build();
        when(accountDeletionTokenRepository.findByToken(digest)).thenReturn(Optional.of(token));
        when(accountDeletionTokenRepository.findUserIdByToken(digest)).thenReturn(Optional.of(52L));
        when(userRepository.findByIdForWrite(52L)).thenReturn(Optional.of(user));
        when(accountDeletionTokenRepository.findByTokenAndUser_Id(digest, 52L)).thenReturn(Optional.of(token));

        accountDeletionService.recoverAccount(raw);

        InOrder order = inOrder(userRepository, accountDeletionTokenRepository);
        order.verify(userRepository).findByIdForWrite(52L);
        order.verify(accountDeletionTokenRepository).findByTokenAndUser_Id(digest, 52L);
        verify(accountDeletionTokenRepository).deleteByUser_Id(52L);
        assertThat(user.isEnabled()).isTrue();
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
