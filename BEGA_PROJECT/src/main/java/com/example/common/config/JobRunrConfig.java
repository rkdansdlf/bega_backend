package com.example.common.config;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class JobRunrConfig {

    /** DbTopologyStartupValidator 와 같은 기준: 영속 저장소가 필수인 프로파일. */
    private static final String[] PERSISTENT_STORAGE_REQUIRED_PROFILES = {"prod", "dev-adb"};

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
    public StorageProvider storageProvider(DataSource dataSource, JobMapper jobMapper, Environment environment) {
        try {
            StorageProvider storageProvider = SqlStorageProviderFactory.using(dataSource);
            storageProvider.setJobMapper(jobMapper);
            return storageProvider;
        } catch (Exception ex) {
            if (environment.acceptsProfiles(Profiles.of(PERSISTENT_STORAGE_REQUIRED_PROFILES))) {
                // 결제 보상 재시도 같은 예약 job 이 프로세스 재시작으로 사라질 수 있으므로,
                // 운영에서는 조용히 강등하지 않고 기동을 실패시킨다.
                log.error("JobRunr SQL StorageProvider 초기화 실패. 운영 프로파일에서는 in-memory fallback 을 허용하지 않아 기동을 중단합니다.", ex);
                throw ex;
            }
            log.error("JobRunr SQL StorageProvider 초기화 실패. in-memory provider 로 fallback 합니다(비운영 프로파일 전용).", ex);
            StorageProvider fallback = new InMemoryStorageProvider();
            fallback.setJobMapper(jobMapper);
            return fallback;
        }
    }
}
