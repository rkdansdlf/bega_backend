package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration;
import org.jobrunr.spring.autoconfigure.storage.JobRunrSqlStorageAutoConfiguration;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * 배경 작업 서버 빈이 실제로 컨텍스트에 올라오는지 검증한다.
 *
 * <p>{@link JobRunrPropertyBindingTest} 는 application.yml 이 스타터 프로퍼티로
 * 바인딩되는지까지만 본다. 그것만으로는 "프로퍼티는 맞는데 빈이 안 뜨는" 경우
 * — 커스텀 {@link StorageProvider} 빈이 자동설정을 밀어내는 상황 등 — 을 잡지 못한다.
 * 운영에서 반복 작업 12건이 등록만 되고 하나도 실행되지 않았던 장애가 정확히
 * "빈이 없는데 에러도 없는" 모습이었기 때문에, 빈 존재 자체를 계약으로 고정한다.
 *
 * <p>{@code JobRunrStarter#startup} 은 {@code @EventListener(ApplicationReadyEvent)} 라서
 * {@link ApplicationContextRunner} 에서는 빈만 만들어지고 폴링 스레드는 뜨지 않는다.
 * 덕분에 DB 없이 빠르게 검증할 수 있다.
 */
class JobRunrBackgroundJobServerContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JobRunrAutoConfiguration.class,
                    JobRunrSqlStorageAutoConfiguration.class));

    @Test
    @DisplayName("커스텀 StorageProvider 가 있어도 BackgroundJobServer 빈이 만들어진다")
    void backgroundJobServerBeanIsCreatedAlongsideACustomStorageProvider() {
        runner.withUserConfiguration(CustomStorageProviderConfiguration.class)
                .withPropertyValues(
                        "jobrunr.background-job-server.enabled=true",
                        "jobrunr.dashboard.enabled=false")
                .run(context -> assertThat(context)
                        .as("반복 작업을 실제로 실행하는 주체는 이 빈뿐이다")
                        .hasSingleBean(BackgroundJobServer.class));
    }

    @Test
    @DisplayName("운영 JobRunrConfig 의 SQL StorageProvider 도 BackgroundJobServer 를 막지 않는다")
    void productionStorageProviderBeanDoesNotSuppressTheBackgroundJobServer() {
        runner.withUserConfiguration(JobRunrConfig.class, EmbeddedDataSourceConfiguration.class)
                .withPropertyValues(
                        "jobrunr.background-job-server.enabled=true",
                        "jobrunr.dashboard.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(BackgroundJobServer.class);
                    assertThat(context)
                            .as("자동설정 StorageProvider 는 @ConditionalOnMissingBean 으로 물러나야 한다")
                            .hasSingleBean(StorageProvider.class);
                    assertThat(context.getBeanNamesForType(StorageProvider.class))
                            .containsExactly("storageProvider");
                });
    }

    @Test
    @DisplayName("레거시 org.jobrunr 접두사로는 BackgroundJobServer 가 뜨지 않는다")
    void legacyPrefixDoesNotEnableTheBackgroundJobServer() {
        runner.withUserConfiguration(CustomStorageProviderConfiguration.class)
                .withPropertyValues(
                        "org.jobrunr.background-job-server.enabled=true",
                        "org.jobrunr.dashboard.enabled=true")
                .run(context -> assertThat(context)
                        .as("8.x 스타터는 이 접두사를 조용히 무시한다 — 이 테스트가 실패하면 접두사가 되돌아온 것이다")
                        .doesNotHaveBean(BackgroundJobServer.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStorageProviderConfiguration {

        @Bean
        StorageProvider storageProvider(JobMapper jobMapper) {
            StorageProvider storageProvider = new InMemoryStorageProvider();
            storageProvider.setJobMapper(jobMapper);
            return storageProvider;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmbeddedDataSourceConfiguration {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true)
                    .build();
        }
    }
}
