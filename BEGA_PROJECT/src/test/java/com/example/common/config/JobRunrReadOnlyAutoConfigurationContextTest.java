package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import com.example.common.readonly.ReadOnlyVerificationInitializer;
import org.jobrunr.dashboard.JobRunrDashboardWebServer;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.AsyncJobPostProcessor;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.RecurringJobPostProcessor;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.spring.autoconfigure.JobRunrStarter;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class JobRunrReadOnlyAutoConfigurationContextTest {

    @Test
    void profileExcludesEveryInstalledJobRunrAutoConfigurationAndCannotRegenerateStorage() {
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        List<String> installed = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates();
        List<String> installedJobRunr = installed.stream().filter(name -> name.startsWith("org.jobrunr.")).toList();
        assertThat(installedJobRunr).hasSize(6);
        List<String> profileExclusions = Binder.get(environment)
                .bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
                .orElseThrow(() -> new AssertionError("Readonly auto-configuration exclusions are missing"));
        assertThat(profileExclusions).containsAll(installedJobRunr);

        // Only the real starter is in this slice. Other auto-configurations must not create external clients.
        List<String> exclusions = new ArrayList<>(installed.stream().filter(name -> !name.startsWith("org.jobrunr.")).toList());
        exclusions.addAll(profileExclusions);
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-autoconfiguration-scope",
                Map.of("spring.autoconfigure.exclude", String.join(",", exclusions))));
        DataSource dataSource = mock(DataSource.class);

        try (MockedStatic<SqlStorageProviderFactory> sqlFactory = mockStatic(SqlStorageProviderFactory.class);
                MockedConstruction<InMemoryStorageProvider> fallback = mockConstruction(InMemoryStorageProvider.class)) {
            new ApplicationContextRunner()
                    .withInitializer(context -> {
                        context.setEnvironment(environment);
                        new ReadOnlyVerificationInitializer().initialize(context);
                    })
                    .withUserConfiguration(StarterSlice.class, JobRunrConfig.class)
                    .withBean(DataSource.class, () -> dataSource)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(Object.class), new String[0],
                                context.getSourceApplicationContext(), Duration.ZERO));
                        assertAll(
                                () -> assertThat(context).doesNotHaveBean(StorageProvider.class),
                                () -> assertThat(context).doesNotHaveBean(InMemoryStorageProvider.class),
                                () -> assertThat(context).doesNotHaveBean(JobMapper.class),
                                () -> assertThat(context).doesNotHaveBean(JobScheduler.class),
                                () -> assertThat(context).doesNotHaveBean(JobRequestScheduler.class),
                                () -> assertThat(context).doesNotHaveBean(BackgroundJobServer.class),
                                () -> assertThat(context).doesNotHaveBean(JobRunrDashboardWebServer.class),
                                () -> assertThat(context).doesNotHaveBean(JobRunrStarter.class),
                                () -> assertThat(context).doesNotHaveBean(RecurringJobPostProcessor.class),
                                () -> assertThat(context).doesNotHaveBean(AsyncJobPostProcessor.class),
                                () -> assertThat(ConditionEvaluationReport.get(context.getBeanFactory()).getExclusions())
                                        .containsAll(installedJobRunr),
                                () -> sqlFactory.verifyNoInteractions(),
                                () -> assertThat(fallback.constructed()).isEmpty(),
                                () -> verifyNoInteractions(dataSource));
                    });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class StarterSlice {
    }
}
