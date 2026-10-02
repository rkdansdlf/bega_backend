package com.example.common.readonly;

import com.example.auth.service.EmailService;
import com.example.common.exception.BusinessException;
import com.example.common.jobs.JobSubmissionGateway;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.service.PayoutClaimService;
import com.example.mate.service.PayoutService;
import com.example.mate.service.PayoutStateService;
import com.example.mate.service.SellerPayoutProfileService;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReadOnlyOperationBoundaryTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-readonly-verification"))
            .withUserConfiguration(BoundaryConfiguration.class)
            .withBean(JobScheduler.class, () -> mock(JobScheduler.class));

    @Test
    void mailRequestsAreRejectedBeforeDisabledOrImmediateSendFallback() {
        JavaMailSender sender = mock(JavaMailSender.class);
        runner.withUserConfiguration(EmailService.class)
                .withBean(JavaMailSender.class, () -> sender)
                .withPropertyValues("app.mail.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EmailService service = context.getBean(EmailService.class);
                    assertUnavailable(() -> service.sendPasswordResetEmail("fixture@example.invalid", "fixture"));
                    assertUnavailable(() -> service.sendPasswordResetEmailJob("fixture@example.invalid", "fixture"));
                    assertUnavailable(() -> service.sendOAuthEmailChallenge("fixture@example.invalid", "fixture", "fixture"));
                    assertUnavailable(() -> service.sendOAuthEmailChallengeJob("fixture@example.invalid", "fixture", "fixture"));
                    assertUnavailable(() -> service.sendNewDeviceLoginEmail("fixture@example.invalid", "", "", "", ""));
                    assertUnavailable(() -> service.sendNewDeviceLoginEmailJob("fixture@example.invalid", "", "", "", ""));
                    assertUnavailable(() -> service.sendAccountDeletionRecoveryEmail("fixture@example.invalid", "fixture", null));
                    assertUnavailable(() -> service.sendAccountDeletionRecoveryEmailJob("fixture@example.invalid", "fixture", null));
                    verifyNoInteractions(sender, context.getBean(JobScheduler.class));
                });
    }

    @Test
    void payoutRequestsAreRejectedBeforeClaimsQueriesOrProviderCalls() {
        PayoutClaimService claims = mock(PayoutClaimService.class);
        PayoutStateService state = mock(PayoutStateService.class);
        PayoutTransactionRepository payouts = mock(PayoutTransactionRepository.class);
        PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
        SellerPayoutProfileService sellers = mock(SellerPayoutProfileService.class);
        runner.withUserConfiguration(PayoutService.class)
                .withBean(PayoutClaimService.class, () -> claims)
                .withBean(PayoutStateService.class, () -> state)
                .withBean(PayoutTransactionRepository.class, () -> payouts)
                .withBean(PaymentTransactionRepository.class, () -> payments)
                .withBean(SellerPayoutProfileService.class, () -> sellers)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PayoutService service = context.getBean(PayoutService.class);
                    assertUnavailable(() -> service.requestPayout(PaymentTransaction.builder().id(1L).build()));
                    assertUnavailable(() -> service.retryPayout(1L));
                    assertUnavailable(service::reconcileDuePayouts);
                    assertUnavailable(service::recoverMissingPayoutClaims);
                    verifyNoInteractions(claims, state, payouts, payments, sellers, context.getBean(JobScheduler.class));
                });
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class).satisfies(error -> {
            BusinessException unavailable = (BusinessException) error;
            assertThat(unavailable.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(unavailable.getCode()).isEqualTo("READ_ONLY_VERIFICATION_UNAVAILABLE");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ReadOnlyVerificationPolicy.class, JobSubmissionGateway.class})
    static class BoundaryConfiguration {
    }
}
