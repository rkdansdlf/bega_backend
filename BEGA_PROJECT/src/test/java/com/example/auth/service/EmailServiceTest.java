package com.example.auth.service;

import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;

import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

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
