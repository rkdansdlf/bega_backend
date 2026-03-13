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
     * JobRunr Storage Provider 설정
     * Polling Interval을 튜닝하여 Main DB 부하를 줄임 ("Noisy Neighbor" 문제 완화)
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
