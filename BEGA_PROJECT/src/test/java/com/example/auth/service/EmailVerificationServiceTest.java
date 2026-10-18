package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.EmailVerificationToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.EmailVerificationTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.RateLimitExceededException;
import com.example.common.ratelimit.RateLimitService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EmailVerificationServiceTest {

    private final EmailVerificationTokenRepository tokenRepository = mock(EmailVerificationTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final EmailVerificationService service = new EmailVerificationService(
            tokenRepository, userRepository, emailService, rateLimitService, true, 1440, true);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void issueForSignupStoresOnlyDigestAndMailsRawToken() {
        UserEntity user = unverifiedUser();
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.issueForSignup(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(tokenRepository).deleteAllByUserId(7L);
        verify(tokenRepository).save(tokenCaptor.capture());
        verify(emailService).sendSignupVerificationEmail(eq("user@example.com"), rawTokenCaptor.capture(), eq(1440L));

        EmailVerificationToken saved = tokenCaptor.getValue();
        String rawToken = rawTokenCaptor.getValue();
        assertThat(saved.getTokenDigest()).hasSize(64).isEqualTo(EmailVerificationService.digest(rawToken));
        assertThat(saved.getTokenDigest()).doesNotContain(rawToken);
        assertThat(saved.getExpiryDate()).isAfter(saved.getCreatedAt().plusMinutes(1439));
        assertThat(saved.isUsed()).isFalse();
    }

    @Test
    void issueForSignupSkipsAlreadyVerifiedUsers() {
        UserEntity user = unverifiedUser();
        user.setEmailVerified(true);

        service.issueForSignup(user);

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendSignupVerificationEmail(anyString(), anyString(), anyLong());
    }

    @Test
    void issueForSignupDoesNotFailSignupWhenMailDeliveryFails() {
        UserEntity user = unverifiedUser();
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("smtp down"))
                .when(emailService).sendSignupVerificationEmail(anyString(), anyString(), anyLong());

        service.issueForSignup(user);

        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void confirmMarksUserVerifiedAndConsumesToken() {
        UserEntity user = unverifiedUser();
        EmailVerificationToken token = token(user, "raw-token", LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenDigestForUpdate(EmailVerificationService.digest("raw-token")))
                .thenReturn(Optional.of(token));

        UserEntity result = service.confirm("raw-token");

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt()).isNotNull();
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(tokenRepository).deleteOtherTokensOfUser(7L, 3L);
    }

    @Test
    void confirmRejectsUnknownUsedAndExpiredTokens() {
        assertThatThrownBy(() -> service.confirm(" "))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN");

        when(tokenRepository.findByTokenDigestForUpdate(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm("unknown"))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN");

        UserEntity user = unverifiedUser();
        EmailVerificationToken used = token(user, "used-token", LocalDateTime.now().plusHours(1));
        used.setUsed(true);
        when(tokenRepository.findByTokenDigestForUpdate(EmailVerificationService.digest("used-token")))
                .thenReturn(Optional.of(used));
        assertThatThrownBy(() -> service.confirm("used-token"))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("EMAIL_VERIFICATION_TOKEN_USED");

        EmailVerificationToken expired = token(user, "expired-token", LocalDateTime.now().minusSeconds(1));
        when(tokenRepository.findByTokenDigestForUpdate(EmailVerificationService.digest("expired-token")))
                .thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.confirm("expired-token"))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("EMAIL_VERIFICATION_TOKEN_EXPIRED");

        assertThat(user.isEmailVerified()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendIssuesNewTokenForUnverifiedLocalAccount() {
        UserEntity user = unverifiedUser();
        when(rateLimitService.isAllowed(anyString(), eq(3), eq(3600), eq(true))).thenReturn(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resend("  USER@example.com ");

        verify(tokenRepository).deleteAllByUserId(7L);
        verify(emailService).sendSignupVerificationEmail(eq("user@example.com"), anyString(), eq(1440L));
    }

    @Test
    void resendIsSilentForUnknownVerifiedOrSocialAccounts() {
        when(rateLimitService.isAllowed(anyString(), eq(3), eq(3600), eq(true))).thenReturn(true);
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        UserEntity verified = unverifiedUser();
        verified.setEmail("verified@example.com");
        verified.setEmailVerified(true);
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verified));
        UserEntity social = unverifiedUser();
        social.setEmail("social@example.com");
        social.setPassword(null);
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.of(social));

        service.resend("missing@example.com");
        service.resend("verified@example.com");
        service.resend("social@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendSignupVerificationEmail(anyString(), anyString(), anyLong());
    }

    @Test
    void resendIsRateLimitedPerEmailBeforeAnyLookup() {
        when(rateLimitService.isAllowed(anyString(), eq(3), eq(3600), eq(true))).thenReturn(false);

        assertThatThrownBy(() -> service.resend("user@example.com"))
                .isInstanceOf(RateLimitExceededException.class);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), eq(3), eq(3600), eq(true));
        assertThat(keyCaptor.getValue()).doesNotContain("user@example.com");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void issueForSignupSendsMailOnlyAfterTransactionCommits() {
        UserEntity user = unverifiedUser();
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.issueForSignup(user);

        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService, never()).sendSignupVerificationEmail(anyString(), anyString(), anyLong());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(emailService).sendSignupVerificationEmail(eq("user@example.com"), anyString(), eq(1440L));
    }

    @Test
    void issueForSignupDoesNotSendMailWhenTransactionRollsBack() {
        UserEntity user = unverifiedUser();
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.issueForSignup(user);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(emailService, never()).sendSignupVerificationEmail(anyString(), anyString(), anyLong());
    }

    @Test
    void resendSendsMailOnlyAfterCommitAndSwallowsAfterCommitFailures() {
        UserEntity user = unverifiedUser();
        when(rateLimitService.isAllowed(anyString(), eq(3), eq(3600), eq(true))).thenReturn(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("Job scheduling is unavailable."))
                .when(emailService).sendSignupVerificationEmail(anyString(), anyString(), anyLong());
        TransactionSynchronizationManager.initSynchronization();

        service.resend("user@example.com");
        verify(emailService, never()).sendSignupVerificationEmail(anyString(), anyString(), anyLong());

        // afterCommit 예외는 Spring 이 호출자에게 전파하므로 서비스가 직접 삼켜야 한다.
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
        verify(emailService).sendSignupVerificationEmail(eq("user@example.com"), anyString(), eq(1440L));
    }

    @Test
    void resendDoesNotRevealAccountWhenMailSendingFails() {
        UserEntity user = unverifiedUser();
        when(rateLimitService.isAllowed(anyString(), eq(3), eq(3600), eq(true))).thenReturn(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(emailService).sendSignupVerificationEmail(anyString(), anyString(), anyLong());

        assertThatCode(() -> service.resend("user@example.com")).doesNotThrowAnyException();

        verify(emailService).sendSignupVerificationEmail(eq("user@example.com"), anyString(), eq(1440L));
    }

    @Test
    void confirmRejectsDisabledOrPendingDeletionAccounts() {
        UserEntity disabled = unverifiedUser();
        disabled.setEnabled(false);
        when(tokenRepository.findByTokenDigestForUpdate(EmailVerificationService.digest("disabled-token")))
                .thenReturn(Optional.of(token(disabled, "disabled-token", LocalDateTime.now().plusHours(1))));
        assertThatThrownBy(() -> service.confirm("disabled-token"))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN");

        UserEntity pendingDeletion = unverifiedUser();
        pendingDeletion.setPendingDeletion(true);
        EmailVerificationToken pendingToken = token(pendingDeletion, "pending-token", LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenDigestForUpdate(EmailVerificationService.digest("pending-token")))
                .thenReturn(Optional.of(pendingToken));
        assertThatThrownBy(() -> service.confirm("pending-token"))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN");

        assertThat(disabled.isEmailVerified()).isFalse();
        assertThat(pendingDeletion.isEmailVerified()).isFalse();
        assertThat(pendingToken.isUsed()).isFalse();
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void confirmWithLeftoverTokenOfAlreadyVerifiedAccountReturnsAlreadyUsed() {
        UserEntity user = unverifiedUser();
        user.markEmailVerified(LocalDateTime.now().minusMinutes(1)); // 예: 비밀번호 재설정으로 인증됨
        EmailVerificationToken leftover = token(user, "leftover-token", LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenDigestForUpdate(EmailVerificationService.digest("leftover-token")))
                .thenReturn(Optional.of(leftover));

        assertThatThrownBy(() -> service.confirm("leftover-token"))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("EMAIL_VERIFICATION_TOKEN_USED");

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).deleteOtherTokensOfUser(anyLong(), anyLong());
    }

    private UserEntity unverifiedUser() {
        return UserEntity.builder()
                .id(7L)
                .email("user@example.com")
                .password("{argon2}hash")
                .handle("@user")
                .name("User")
                .role("ROLE_USER")
                .enabled(true)
                .emailVerified(false)
                .build();
    }

    private EmailVerificationToken token(UserEntity user, String rawToken, LocalDateTime expiry) {
        return EmailVerificationToken.builder()
                .id(3L)
                .user(user)
                .tokenDigest(EmailVerificationService.digest(rawToken))
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .expiryDate(expiry)
                .used(false)
                .build();
    }
}
