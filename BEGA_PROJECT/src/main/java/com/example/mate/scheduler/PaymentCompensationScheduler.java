package com.example.mate.scheduler;

import com.example.mate.service.PaymentIntentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompensationScheduler implements ApplicationRunner {

    private final JobScheduler jobScheduler;
    private final PaymentIntentService paymentIntentService;

    @Override
    public void run(ApplicationArguments args) {
        jobScheduler.scheduleRecurrently(
                "payment-intent-reconcile",
                "*/15 * * * *",
                paymentIntentService::reconcileCompensationTargets);
        log.info("Registered payment compensation reconciliation job");
    }
}
