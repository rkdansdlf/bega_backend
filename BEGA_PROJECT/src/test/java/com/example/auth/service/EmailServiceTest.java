package com.example.auth.service;

import com.example.common.jobs.JobSubmissionGateway;
import com.example.common.readonly.ReadOnlyVerificationPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.navigation.AmountRequest;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String TEST_TOKEN_ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private JobScheduler jobScheduler;

    @Test
    void disabledMailSkipsEnqueueAndDelivery() {
        EmailService emailService = new EmailService(
                mailSender,
                gateway(objectProvider(jobScheduler)),
                tokenCipher(),
                false);

        emailService.sendPasswordResetEmail("user@example.com", "token");
        emailService.sendNewDeviceLoginEmail("user@example.com", "MacBook", "Chrome", "macOS", "127.0.0.1");
        emailService.sendAccountDeletionRecoveryEmail("user@example.com", "recovery-token", LocalDateTime.now());

        emailService.sendPasswordResetEmailJob("user@example.com", "token");
        emailService.sendNewDeviceLoginEmailJob("user@example.com", "MacBook", "Chrome", "macOS", "127.0.0.1");
        emailService.sendAccountDeletionRecoveryEmailJob("user@example.com", "recovery-token", LocalDateTime.now());

        verifyNoInteractions(jobScheduler, mailSender);
    }

    @Test
    void enabledMailEnqueuesJobsWhenSchedulerAvailable() {
        EmailService emailService = new EmailService(
                mailSender,
                gateway(objectProvider(jobScheduler)),
                tokenCipher(),
                true);

        emailService.sendPasswordResetEmail("user@example.com", "token");
        emailService.sendNewDeviceLoginEmail("user@example.com", "MacBook", "Chrome", "macOS", "127.0.0.1");
        emailService.sendAccountDeletionRecoveryEmail("user@example.com", "recovery-token", LocalDateTime.now());

        verify(jobScheduler, times(3)).enqueue(org.mockito.Mockito.<IocJobLambda<Object>>any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void queuedRecoveryTokensAreEncryptedInJobRunrSerializedPayload() {
        InMemoryStorageProvider storage = new InMemoryStorageProvider();
        JobMapper jobMapper = new JobMapper(new JacksonJsonMapper());
        storage.setJobMapper(jobMapper);
        JobScheduler inMemoryScheduler = new JobScheduler(storage);
        EmailService emailService = createEnabledEmailService(objectProvider(inMemoryScheduler));
        String resetToken = "synthetic-reset-token-for-serialization-test";
        String recoveryToken = "synthetic-recovery-token-for-serialization-test";

        try {
            emailService.sendPasswordResetEmail("reset@example.test", resetToken);
            emailService.sendAccountDeletionRecoveryEmail(
                    "recovery@example.test",
                    recoveryToken,
                    LocalDateTime.of(2026, 3, 12, 9, 0));

            List<Job> jobs = storage.getJobList(
                    StateName.ENQUEUED,
                    new AmountRequest("createdAt:ASC", 10));
            assertThat(jobs).hasSize(2);
            String serializedJobs = jobs.stream()
                    .map(jobMapper::serializeJob)
                    .reduce("", (allJobs, job) -> allJobs + job);
            assertThat(serializedJobs)
                    .contains(EmailJobTokenCipher.ENVELOPE_PREFIX)
                    .doesNotContain(resetToken)
                    .doesNotContain(recoveryToken);

            Job resetJob = jobs.stream()
                    .filter(job -> job.getJobDetails().getMethodName().equals("sendPasswordResetEmailJob"))
                    .findFirst()
                    .orElseThrow();
            Object[] resetArguments = resetJob.getJobDetails().getJobParameterValues();
            emailService.sendPasswordResetEmailJob(
                    (String) resetArguments[0],
                    (String) resetArguments[1],
                    (String) resetArguments[2]);

            Job recoveryJob = jobs.stream()
                    .filter(job -> job.getJobDetails().getMethodName().equals("sendAccountDeletionRecoveryEmailJob"))
                    .findFirst()
                    .orElseThrow();
            Object[] recoveryArguments = recoveryJob.getJobDetails().getJobParameterValues();
            emailService.sendAccountDeletionRecoveryEmailJob(
                    (String) recoveryArguments[0],
                    (String) recoveryArguments[1],
                    (LocalDateTime) recoveryArguments[2],
                    (String) recoveryArguments[3]);

            verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
        } finally {
            storage.close();
        }
    }

    @Test
    void missingEncryptionKeyFallsBackToImmediateDeliveryWithoutEnqueueing() {
        EmailService emailService = new EmailService(
                mailSender,
                gateway(objectProvider(jobScheduler)),
                new EmailJobTokenCipher(""),
                true);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://frontend.test");

        emailService.sendPasswordResetEmail("user@example.com", "synthetic-reset-token");

        verify(jobScheduler, never()).enqueue(org.mockito.Mockito.<IocJobLambda<Object>>any());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void oauthChallengeTokenIsSentWithoutPersistingItInTheJobQueue() {
        EmailService emailService = createEnabledEmailService(objectProvider(jobScheduler));

        emailService.sendOAuthEmailChallenge(
                "user@example.com",
                "challenge-id",
                "single-use-raw-token");

        verifyNoInteractions(jobScheduler);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void enabledMailSendsImmediatelyWhenSchedulerUnavailable() {
        EmailService emailService = createEnabledEmailService(objectProvider(null));

        emailService.sendPasswordResetEmail("user@example.com", "token");
        emailService.sendNewDeviceLoginEmail("user@example.com", "MacBook", "Chrome", "macOS", "127.0.0.1");
        emailService.sendAccountDeletionRecoveryEmail("user@example.com", "recovery-token", LocalDateTime.now());

        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    void enabledMailFallsBackToImmediateSendWhenEnqueueFails() {
        doThrow(new IllegalStateException("queue down"))
                .when(jobScheduler)
                .enqueue(org.mockito.Mockito.<IocJobLambda<Object>>any());

        EmailService emailService = createEnabledEmailService(objectProvider(jobScheduler));

        emailService.sendPasswordResetEmail("user@example.com", "token");
        emailService.sendNewDeviceLoginEmail("user@example.com", "MacBook", "Chrome", "macOS", "127.0.0.1");
        emailService.sendAccountDeletionRecoveryEmail("user@example.com", "recovery-token", LocalDateTime.now());

        verify(jobScheduler, times(3)).enqueue(org.mockito.Mockito.<IocJobLambda<Object>>any());
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    void passwordResetEmailJobIncludesSanitizedRedirect() {
        EmailService emailService = createEnabledEmailService(objectProvider(null));
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendPasswordResetEmailJob("user@example.com", "token", "/mypage?view=accountSettings");

        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getText())
                .contains("https://frontend.test/password/reset/confirm?token=token&redirect=/mypage?view%3DaccountSettings");
    }

    @Test
    void accountDeletionRecoveryEmailJobIncludesSanitizedRedirect() {
        EmailService emailService = createEnabledEmailService(objectProvider(null));
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendAccountDeletionRecoveryEmailJob(
                "user@example.com",
                "recovery-token",
                LocalDateTime.of(2026, 3, 12, 9, 0),
                "/mypage?view=accountSettings");

        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getText())
                .contains("https://frontend.test/account/deletion/recovery?token=recovery-token&redirect=/mypage?view%3DaccountSettings");
    }

    @Test
    void passwordResetEmailJobDropsUnsafeRedirect() {
        EmailService emailService = createEnabledEmailService(objectProvider(null));
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendPasswordResetEmailJob("user@example.com", "token", "https://evil.example");

        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getText())
                .contains("https://frontend.test/password/reset/confirm?token=token")
                .doesNotContain("redirect=");
    }

    private EmailService createEnabledEmailService(ObjectProvider<JobScheduler> provider) {
        EmailService emailService = new EmailService(mailSender, gateway(provider), tokenCipher(), true);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://frontend.test");
        return emailService;
    }

    private EmailJobTokenCipher tokenCipher() {
        return new EmailJobTokenCipher(TEST_TOKEN_ENCRYPTION_KEY);
    }

    private JobSubmissionGateway gateway(ObjectProvider<JobScheduler> provider) {
        return new JobSubmissionGateway(provider, new ReadOnlyVerificationPolicy(new MockEnvironment()));
    }

    private ObjectProvider<JobScheduler> objectProvider(JobScheduler scheduler) {
        return new ObjectProvider<>() {
            @Override
            public JobScheduler getObject(Object... args) {
                return scheduler;
            }

            @Override
            public JobScheduler getIfAvailable() {
                return scheduler;
            }

            @Override
            public JobScheduler getIfUnique() {
                return scheduler;
            }

            @Override
            public JobScheduler getObject() {
                return scheduler;
            }
        };
    }
}
