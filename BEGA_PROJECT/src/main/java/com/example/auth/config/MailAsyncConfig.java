package com.example.auth.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * JobRunr 에 넣지 못한 메일을 즉시 보낼 때 쓰는 별도 스레드 풀.
 * SMTP 호출을 요청/트랜잭션 스레드에서 떼어내 DB 커넥션을 붙잡고 있지 않게 한다.
 */
@Configuration
public class MailAsyncConfig {

    public static final String MAIL_FALLBACK_TASK_EXECUTOR = "mailFallbackTaskExecutor";

    @Bean(name = MAIL_FALLBACK_TASK_EXECUTOR)
    public TaskExecutor mailFallbackTaskExecutor(
            @Value("${app.mail.fallback.core-pool-size:1}") int corePoolSize,
            @Value("${app.mail.fallback.max-pool-size:2}") int maxPoolSize,
            @Value("${app.mail.fallback.queue-capacity:100}") int queueCapacity) {
        int normalizedCorePoolSize = Math.max(1, corePoolSize);
        int normalizedMaxPoolSize = Math.max(normalizedCorePoolSize, maxPoolSize);
        int normalizedQueueCapacity = Math.max(0, queueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-fallback-");
        executor.setCorePoolSize(normalizedCorePoolSize);
        executor.setMaxPoolSize(normalizedMaxPoolSize);
        executor.setQueueCapacity(normalizedQueueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
