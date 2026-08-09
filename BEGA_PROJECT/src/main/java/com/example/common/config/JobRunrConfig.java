package com.example.common.config;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class JobRunrConfig {

    /**
     * JobRunr Storage Provider 설정.
     *
     * <p>SQL StorageProvider 를 직접 만들어 두면 스타터의
     * {@code JobRunrSqlStorageAutoConfiguration#sqlStorageProvider} 가
     * {@code @ConditionalOnMissingBean} 으로 물러나고, 초기화 실패 시 위 로그/폴백 경로를
     * 우리가 통제할 수 있다. BackgroundJobServer·dashboard 자동설정은 이 빈과 무관하며
     * {@code jobrunr.background-job-server.enabled} / {@code jobrunr.dashboard.enabled}
     * 프로퍼티로만 결정된다.
     *
     * <p>주의: 이 빈은 폴링 주기를 튜닝하지 않는다("Noisy Neighbor" 완화라는 예전 주석은
     * 구현된 적이 없다). 실제 조절점은 application.yml 의
     * {@code jobrunr.background-job-server.poll-interval-in-seconds} 다.
     */
    @Bean
    public StorageProvider storageProvider(DataSource dataSource, JobMapper jobMapper) {
        try {
            StorageProvider storageProvider = SqlStorageProviderFactory.using(dataSource);
            storageProvider.setJobMapper(jobMapper);
            return storageProvider;
        } catch (Exception ex) {
            log.error("JobRunr SQL StorageProvider 초기화 실패. In-memory provider로 fallback합니다.", ex);
            try {
                Class<?> clazz = Class.forName("org.jobrunr.storage.InMemoryStorageProvider");
                Object fallback = clazz.getDeclaredConstructor().newInstance();
                if (fallback instanceof StorageProvider fallbackProvider) {
                    fallbackProvider.setJobMapper(jobMapper);
                    return fallbackProvider;
                }
            } catch (Exception reflectionEx) {
                log.error("JobRunr In-memory fallback 초기화 실패", reflectionEx);
            }
            throw ex;
        }
    }
}
