package com.example.auth.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AccountSecurityAsyncConfig {

    @Bean(name = "accountSecurityTaskExecutor")
    public TaskExecutor accountSecurityTaskExecutor(
            @Value("${app.auth.security-bookkeeping.core-pool-size:1}") int corePoolSize,
            @Value("${app.auth.security-bookkeeping.max-pool-size:4}") int maxPoolSize,
            @Value("${app.auth.security-bookkeeping.queue-capacity:200}") int queueCapacity) {
        int normalizedCorePoolSize = Math.max(1, corePoolSize);
        int normalizedMaxPoolSize = Math.max(normalizedCorePoolSize, maxPoolSize);
        int normalizedQueueCapacity = Math.max(0, queueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("account-security-");
        executor.setCorePoolSize(normalizedCorePoolSize);
        executor.setMaxPoolSize(normalizedMaxPoolSize);
        executor.setQueueCapacity(normalizedQueueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
