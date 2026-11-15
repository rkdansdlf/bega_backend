package com.example.common.config;

import com.example.common.readonly.ReadOnlyVerificationPolicy;
import org.springframework.context.annotation.Profile;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.function.Supplier;

@Configuration
@Profile("!" + ReadOnlyVerificationPolicy.PROFILE)
@Slf4j
public class JobRunrConfig {

    /**
     * JobRunr Storage Provider 설정.
     *
     * <p>SQL StorageProvider 를 직접 만들어 두면 스타터의
     * {@code JobRunrSqlStorageAutoConfiguration#sqlStorageProvider} 가
     * {@code @ConditionalOnMissingBean} 으로 물러나고, 초기화 실패 시 폴백 여부를
     * 우리가 통제할 수 있다(기본은 기동 중단, {@code app.jobrunr.allow-in-memory-fallback}). BackgroundJobServer·dashboard 자동설정은 이 빈과 무관하며
     * {@code jobrunr.background-job-server.enabled} / {@code jobrunr.dashboard.enabled}
     * 프로퍼티로만 결정된다.
     *
     * <p>주의: 이 빈은 폴링 주기를 튜닝하지 않는다("Noisy Neighbor" 완화라는 예전 주석은
     * 구현된 적이 없다). 실제 조절점은 application.yml 의
     * {@code jobrunr.background-job-server.poll-interval-in-seconds} 다.
     */
    @Bean
    public StorageProvider storageProvider(
            DataSource dataSource,
            JobMapper jobMapper,
            Environment environment,
            @Value("${app.jobrunr.allow-in-memory-fallback:false}") boolean allowInMemoryFallback) {
        assertFallbackPolicy(environment.getActiveProfiles(), allowInMemoryFallback);
        return createStorageProvider(
                () -> {
                    StorageProvider sql = SqlStorageProviderFactory.using(dataSource);
                    sql.setJobMapper(jobMapper);
                    return sql;
                },
                () -> {
                    InMemoryStorageProvider inMemory = new InMemoryStorageProvider();
                    inMemory.setJobMapper(jobMapper);
                    return inMemory;
                },
                allowInMemoryFallback);
    }

    /**
     * prod 에서는 In-memory 폴백을 켤 수 없다. 재시작하면 결제·계정 삭제 같은 durable job 이
     * 사라지는데 로그 한 줄만 남고 기동은 성공하기 때문이다. 프로필 이름 허용목록이 아니라
     * 명시적 정책 값({@code app.jobrunr.allow-in-memory-fallback}, 기본 false)으로 막으므로
     * 새 프로필이 추가돼도 조용히 폴백하지 않는다.
     */
    static void assertFallbackPolicy(String[] activeProfiles, boolean allowInMemoryFallback) {
        if (allowInMemoryFallback && Arrays.stream(activeProfiles).anyMatch(p -> "prod".equalsIgnoreCase(p))) {
            throw new IllegalStateException(
                    "app.jobrunr.allow-in-memory-fallback=true is not allowed in the prod profile "
                            + "(in-memory JobRunr storage loses durable jobs on restart)");
        }
    }

    static StorageProvider createStorageProvider(
            Supplier<StorageProvider> sqlProvider,
            Supplier<StorageProvider> inMemoryProvider,
            boolean allowInMemoryFallback) {
        try {
            return sqlProvider.get();
        } catch (RuntimeException ex) {
            if (!allowInMemoryFallback) {
                log.error("JobRunr SQL StorageProvider 초기화 실패. 애플리케이션 기동을 중단합니다. "
                        + "(개발 환경에서만 APP_JOBRUNR_ALLOW_IN_MEMORY_FALLBACK=true 로 In-memory 를 허용할 수 있다)", ex);
                throw ex;
            }
            log.error("JobRunr SQL StorageProvider 초기화 실패. 명시 설정에 따라 In-memory provider로 fallback합니다. "
                    + "재시작 시 작업이 사라집니다.", ex);
            return inMemoryProvider.get();
        }
    }
}
