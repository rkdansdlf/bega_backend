package com.example.common.readonly;

import com.example.common.jobs.JobSubmissionGateway;
import com.example.common.exception.GlobalExceptionHandler;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobSubmissionGatewayTest {

    @Test
    void readOnlyNeverResolvesSchedulerAndNeverPretendsToSubmitJobs() {
        ObjectProvider<JobScheduler> provider = mock(ObjectProvider.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", ReadOnlyVerificationPolicy.PROFILE);
        JobSubmissionGateway gateway = new JobSubmissionGateway(provider, new ReadOnlyVerificationPolicy(environment));

        assertThat(gateway.isAvailable()).isFalse();
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> gateway.schedule(Instant.EPOCH, () -> {}))
                    .isInstanceOf(ReadOnlyVerificationUnavailableException.class);
            assertThatThrownBy(() -> gateway.enqueue((Runnable job) -> job.run()))
                    .isInstanceOf(ReadOnlyVerificationUnavailableException.class);
        }
        verifyNoInteractions(provider);
    }

    @Test
    void normalModeDelegatesUnchangedJobsAndReturnsActualIds() {
        ObjectProvider<JobScheduler> provider = mock(ObjectProvider.class);
        JobScheduler scheduler = mock(JobScheduler.class);
        when(provider.getIfAvailable()).thenReturn(scheduler);
        JobSubmissionGateway gateway = new JobSubmissionGateway(provider,
                new ReadOnlyVerificationPolicy(new MockEnvironment()));
        JobLambda scheduled = () -> {};
        IocJobLambda<Runnable> enqueued = Runnable::run;
        JobId scheduledId = new JobId(UUID.randomUUID());
        JobId enqueuedId = new JobId(UUID.randomUUID());
        when(scheduler.schedule(Instant.EPOCH, scheduled)).thenReturn(scheduledId);
        when(scheduler.enqueue(enqueued)).thenReturn(enqueuedId);

        assertThat(gateway.isAvailable()).isTrue();
        assertThat(gateway.schedule(Instant.EPOCH, scheduled)).isSameAs(scheduledId);
        assertThat(gateway.enqueue(enqueued)).isSameAs(enqueuedId);
        verify(scheduler).schedule(Instant.EPOCH, scheduled);
        verify(scheduler).enqueue(enqueued);
    }

    @Test
    void absentNormalSchedulerAllowsExplicitMailFallbackButNotFakeJobSuccess() {
        ObjectProvider<JobScheduler> provider = mock(ObjectProvider.class);
        JobSubmissionGateway gateway = new JobSubmissionGateway(provider,
                new ReadOnlyVerificationPolicy(new MockEnvironment()));

        gateway.requireWritable();
        assertThat(gateway.isAvailable()).isFalse();
        assertThatThrownBy(() -> gateway.schedule(Instant.EPOCH, () -> {}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gateway.enqueue((Runnable job) -> job.run()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unavailableUsesExistingHttpErrorEnvelopeInsteadOfSuccess() throws Exception {
        ObjectProvider<JobScheduler> provider = mock(ObjectProvider.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(ReadOnlyVerificationPolicy.PROFILE);
        JobSubmissionGateway gateway = new JobSubmissionGateway(provider, new ReadOnlyVerificationPolicy(environment));
        var mvc = MockMvcBuilders.standaloneSetup(new SubmissionProbe(gateway))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/readonly-submission-probe"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("READ_ONLY_VERIFICATION_UNAVAILABLE"));
        verifyNoInteractions(provider);
    }

    @RestController
    static class SubmissionProbe {
        private final JobSubmissionGateway gateway;

        SubmissionProbe(JobSubmissionGateway gateway) {
            this.gateway = gateway;
        }

        @PostMapping("/readonly-submission-probe")
        String submit() {
            return gateway.schedule(Instant.EPOCH, () -> {}).toString();
        }
    }
}
