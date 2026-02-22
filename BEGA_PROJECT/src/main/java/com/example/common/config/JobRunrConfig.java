package com.example.common.config;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class JobRunrConfig {

    /**
     * JobRunr Storage Provider 설정
     * Polling Interval을 튜닝하여 Main DB 부하를 줄임 ("Noisy Neighbor" 문제 완화)
     */
    @Bean
    public StorageProvider storageProvider(DataSource dataSource, JobMapper jobMapper) {
        StorageProvider storageProvider = SqlStorageProviderFactory.using(dataSource);
        storageProvider.setJobMapper(jobMapper);
        return storageProvider;
    }
}
