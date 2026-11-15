package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.example.auth.entity.OAuthEmailChallenge;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.OAuthEmailChallengeRepository;
import com.example.common.ratelimit.RateLimitService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuthEmailChallengeServiceTest {

    private final OAuthEmailChallengeRepository repository = org.mockito.Mockito.mock(OAuthEmailChallengeRepository.class);
    private final EmailService emailService = org.mockito.Mockito.mock(EmailService.class);
    private final OAuthNewAccountService newAccountService = org.mockito.Mockito.mock(OAuthNewAccountService.class);
    private final RateLimitService rateLimitService = org.mockito.Mockito.mock(RateLimitService.class);
    private final OAuthEmailChallengeService service = new OAuthEmailChallengeService(
            repository,
            emailService,
            newAccountService,
            rateLimitService);

    @Test
    void issuePersistsOnlyDigestAndSendsRawOneTimeToken() {
        when(rateLimitService.isAllowed(any(String.class), eq(3), eq(3600), eq(true))).thenReturn(true);
        when(repository.save(any(OAuthEmailChallenge.class))).thenAnswer(invocation -> {
            OAuthEmailChallenge challenge = invocation.getArgument(0);
            challenge.setId(11L);
            return challenge;
        });

        String challengeId = service.issue("kakao", "provider-1", "user@example.com", "User", null);

        ArgumentCaptor<OAuthEmailChallenge> challengeCaptor = ArgumentCaptor.forClass(OAuthEmailChallenge.class);
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).save(challengeCaptor.capture());
        verify(emailService).sendOAuthEmailChallenge(eq("user@example.com"), eq(challengeId), rawTokenCaptor.capture());
        assertThat(challengeCaptor.getValue().getTokenDigest()).hasSize(64);
        assertThat(challengeCaptor.getValue().getTokenDigest()).doesNotContain(rawTokenCaptor.getValue());
        assertThat(challengeCaptor.getValue().getExpiryDate())
                .isAfter(challengeCaptor.getValue().getCreatedAt().plusMinutes(29));
    }

    @Test
    void emailRequiredChallengeCanSubmitEmailAndTransitionsToSent() {
        when(repository.save(any(OAuthEmailChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rateLimitService.isAllowed(any(String.class), eq(3), eq(3600), eq(true))).thenReturn(true);
        OAuthEmailChallenge challenge = OAuthEmailChallenge.builder()
                .challengeId("challenge-public-id")
                .provider("naver")
                .providerId("provider-no-email")
                .status("EMAIL_REQUIRED")
                .createdAt(java.time.LocalDateTime.now())
                .used(false)
                .build();
        when(repository.findByChallengeIdForUpdate("challenge-public-id")).thenReturn(Optional.of(challenge));

        service.submitEmail("challenge-public-id", "user@example.com");

        assertThat(challenge.getStatus()).isEqualTo("EMAIL_SENT");
        assertThat(challenge.getEmail()).isEqualTo("user@example.com");
        assertThat(challenge.getTokenDigest()).hasSize(64);
        verify(emailService).sendOAuthEmailChallenge(
                eq("user@example.com"), eq("challenge-public-id"), any(String.class));
    }

    @Test
    void confirmConsumesChallengeOnceAndCreatesLinkedAccount() {
        String rawToken = "challenge-token";
        OAuthEmailChallenge challenge = OAuthEmailChallenge.builder()
                .tokenDigest(OAuthEmailChallengeService.digest(rawToken))
                .provider("naver")
                .providerId("provider-2")
                .email("user@example.com")
                .name("User")
                .createdAt(java.time.LocalDateTime.now().minusMinutes(1))
                .expiryDate(java.time.LocalDateTime.now().plusMinutes(29))
                .used(false)
                .build();
        UserEntity created = UserEntity.builder().id(7L).email("user@example.com").build();
        when(repository.findByTokenDigestForUpdate(OAuthEmailChallengeService.digest(rawToken)))
                .thenReturn(Optional.of(challenge));
        when(newAccountService.create("user@example.com", "User", "naver", "provider-2", null))
                .thenReturn(created);

        Long userId = service.confirm(rawToken);

        assertThat(userId).isEqualTo(7L);
        assertThat(challenge.isUsed()).isTrue();
        assertThat(challenge.getTokenDigest()).startsWith("used:");
    }
}
