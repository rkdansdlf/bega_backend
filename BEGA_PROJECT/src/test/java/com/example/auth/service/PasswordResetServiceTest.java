package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.dto.PasswordResetConfirmDto;
import com.example.auth.dto.PasswordResetRequestDto;
import com.example.auth.entity.PasswordResetToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    @Test
    void requestPasswordReset_suppressesUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatCode(() -> passwordResetService.requestPasswordReset(
                new PasswordResetRequestDto("missing@example.com", null)))
                .doesNotThrowAnyException();

        verify(authSecurityMonitoringService).recordPasswordResetSuppressed();
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void requestPasswordReset_suppressesSocialOnlyAccount() {
        UserEntity socialUser = UserEntity.builder()
                .id(11L)
                .uniqueId(UUID.randomUUID())
                .email("social@example.com")
                .role("ROLE_USER")
                .name("social")
                .handle("@social")
                .password(null)
                .build();
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.of(socialUser));

        assertThatCode(() -> passwordResetService.requestPasswordReset(
                new PasswordResetRequestDto("social@example.com", null)))
                .doesNotThrowAnyException();

        verify(authSecurityMonitoringService).recordPasswordResetSuppressed();
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void requestPasswordReset_forwardsRedirectToEmailLink() {
        UserEntity user = UserEntity.builder()
                .id(12L)
                .uniqueId(UUID.randomUUID())
                .email("user@example.com")
                .role("ROLE_USER")
                .name("user")
                .handle("@user")
                .password("hashed-password")
                .build();
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset(
                new PasswordResetRequestDto("user@example.com", "/mypage?view=accountSettings"));

        verify(tokenRepository).deleteByUserId(12L);
        verify(tokenRepository).save(tokenCaptor.capture());
        verify(emailService).sendPasswordResetEmail(
                eq("user@example.com"),
                eq(tokenCaptor.getValue().getToken()),
                eq("/mypage?view=accountSettings"));
    }

    @Test
    void confirmPasswordReset_invalidatesExistingSessions() {
        UserEntity user = UserEntity.builder()
                .id(21L)
                .uniqueId(UUID.randomUUID())
                .email("user@example.com")
                .role("ROLE_USER")
                .name("user")
                .handle("@user")
                .password("old-password")
                .tokenVersion(4)
                .build();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token("reset-token")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();
        PasswordResetConfirmDto request = new PasswordResetConfirmDto(
                "reset-token",
                "NewPassword1!",
                "NewPassword1!");

        when(tokenRepository.findByToken("reset-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-password");

        passwordResetService.confirmPasswordReset(request);

        verify(userRepository).save(user);
        verify(refreshRepository).deleteByEmail("user@example.com");
        verify(tokenRepository).save(resetToken);
    }
}
