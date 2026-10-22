package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.auth.entity.OAuthEmailChallenge;
import com.example.auth.repository.OAuthEmailChallengeRepository;
import com.example.common.ratelimit.RateLimitService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(OAuthEmailChallengeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OAuthEmailChallengeTransactionIntegrationTest {

    @Autowired
    private OAuthEmailChallengeService challengeService;

    @Autowired
    private OAuthEmailChallengeRepository challengeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private OAuthNewAccountService newAccountService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @Test
    void emailedChallengeSurvivesOAuthCallbackRollback() {
        String providerId = "synthetic-google-" + UUID.randomUUID();
        when(rateLimitService.isAllowed(anyString(), eq(3), eq(3600), eq(true))).thenReturn(true);

        String challengeId = issueThenRollBackOAuthCallback(() -> challengeService.issue(
                "google", providerId, "New.User@example.com", "Synthetic User", null));

        OAuthEmailChallenge challenge = challengeRepository.findByChallengeId(challengeId).orElseThrow();
        assertThat(challenge.getProvider()).isEqualTo("google");
        assertThat(challenge.getProviderId()).isEqualTo(providerId);
        assertThat(challenge.getEmail()).isEqualTo("new.user@example.com");
        assertThat(challenge.getStatus()).isEqualTo("EMAIL_SENT");
        assertThat(challenge.getTokenDigest()).hasSize(64);
        assertThat(challenge.isUsed()).isFalse();
        verify(emailService).sendOAuthEmailChallenge(eq("new.user@example.com"), eq(challengeId), anyString());

        deleteChallenge("google", providerId);
    }

    @Test
    void emailRequiredChallengeSurvivesOAuthCallbackRollbackWithoutSendingMail() {
        String providerId = "synthetic-naver-" + UUID.randomUUID();

        String challengeId = issueThenRollBackOAuthCallback(() -> challengeService.issueEmailRequired(
                "naver", providerId, "Synthetic User", null));

        OAuthEmailChallenge challenge = challengeRepository.findByChallengeId(challengeId).orElseThrow();
        assertThat(challenge.getProvider()).isEqualTo("naver");
        assertThat(challenge.getProviderId()).isEqualTo(providerId);
        assertThat(challenge.getEmail()).isNull();
        assertThat(challenge.getTokenDigest()).isNull();
        assertThat(challenge.getStatus()).isEqualTo("EMAIL_REQUIRED");
        assertThat(challenge.isUsed()).isFalse();
        verifyNoInteractions(emailService);

        deleteChallenge("naver", providerId);
    }

    private String issueThenRollBackOAuthCallback(Supplier<String> issueChallenge) {
        AtomicReference<String> challengeId = new AtomicReference<>();
        TransactionTemplate callbackTransaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> callbackTransaction.executeWithoutResult(status -> {
            challengeId.set(issueChallenge.get());
            throw new OAuth2AuthenticationException("oauth2_email_verification_required");
        }))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode())
                                .isEqualTo("oauth2_email_verification_required"));

        return challengeId.get();
    }

    private void deleteChallenge(String provider, String providerId) {
        TransactionTemplate cleanupTransaction = new TransactionTemplate(transactionManager);
        cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanupTransaction.executeWithoutResult(status ->
                challengeRepository.deleteByProviderAndProviderId(provider, providerId));
    }
}
