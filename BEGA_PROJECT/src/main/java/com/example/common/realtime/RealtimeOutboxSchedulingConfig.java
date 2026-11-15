package com.example.common.realtime;

import com.example.common.readonly.ReadOnlyVerificationPolicy;
import org.springframework.context.annotation.Profile;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@Profile("!" + ReadOnlyVerificationPolicy.PROFILE)
@ConditionalOnProperty(
        prefix = "app.realtime.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RealtimeOutboxSchedulingConfig {

    @Bean(name = "realtimeOutboxTaskScheduler", destroyMethod = "shutdown")
    ThreadPoolTaskScheduler realtimeOutboxTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("realtime-outbox-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
