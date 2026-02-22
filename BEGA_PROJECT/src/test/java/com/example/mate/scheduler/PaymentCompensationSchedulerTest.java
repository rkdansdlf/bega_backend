package com.example.mate.scheduler;

import com.example.mate.service.PaymentIntentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompensationSchedulerTest {

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private PaymentIntentService paymentIntentService;

    @InjectMocks
    private PaymentCompensationScheduler paymentCompensationScheduler;

    @Test
    void run_registersPaymentCompensationJobWithExpectedIdAndCron() {
        paymentCompensationScheduler.run(null);

        verify(jobScheduler).scheduleRecurrently(
                eq("payment-intent-reconcile"),
                eq("*/15 * * * *"),
                any(JobLambda.class));
    }
}
