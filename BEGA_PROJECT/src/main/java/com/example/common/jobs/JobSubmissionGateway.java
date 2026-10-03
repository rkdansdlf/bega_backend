package com.example.common.jobs;

import com.example.common.readonly.ReadOnlyVerificationPolicy;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JobSubmissionGateway {

    private final JobScheduler scheduler;
    private final ReadOnlyVerificationPolicy policy;

    public JobSubmissionGateway(ObjectProvider<JobScheduler> schedulerProvider, ReadOnlyVerificationPolicy policy) {
        this.policy = policy;
        this.scheduler = policy.isReadOnly() ? null : schedulerProvider.getIfAvailable();
    }

    public void requireWritable() {
        policy.requireWritable();
    }

    public boolean isAvailable() {
        return scheduler != null;
    }

    public JobId schedule(Instant instant, JobLambda job) {
        return requireScheduler().schedule(instant, job);
    }

    public <T> JobId enqueue(IocJobLambda<T> job) {
        return requireScheduler().enqueue(job);
    }

    private JobScheduler requireScheduler() {
        requireWritable();
        if (scheduler == null) {
            throw new IllegalStateException("Job scheduling is unavailable.");
        }
        return scheduler;
    }
}
