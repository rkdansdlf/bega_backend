package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;

import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private JobScheduler jobScheduler;

    @Test
    void disabledMailSkipsEnqueueAndDelivery() {
        EmailService emailService = new EmailService(mailSender, objectProvider(jobScheduler), false);

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
        EmailService emailService = new EmailService(mailSender, objectProvider(jobScheduler), true);

        emailService.sendPasswordResetEmail("user@example.com", "token");
        emailService.sendNewDeviceLoginEmail("user@example.com", "MacBook", "Chrome", "macOS", "127.0.0.1");
        emailService.sendAccountDeletionRecoveryEmail("user@example.com", "recovery-token", LocalDateTime.now());

        verify(jobScheduler, times(3)).enqueue(any(IocJobLambda.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
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
                .enqueue(any(IocJobLambda.class));

        EmailService emailService = createEnabledEmailService(objectProvider(jobScheduler));

        emailService.sendPasswordResetEmail("user@example.com", "token");
        emailService.sendNewDeviceLoginEmail("user@example.com", "MacBook", "Chrome", "macOS", "127.0.0.1");
        emailService.sendAccountDeletionRecoveryEmail("user@example.com", "recovery-token", LocalDateTime.now());

        verify(jobScheduler, times(3)).enqueue(any(IocJobLambda.class));
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
        EmailService emailService = new EmailService(mailSender, provider, true);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://frontend.test");
        return emailService;
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
