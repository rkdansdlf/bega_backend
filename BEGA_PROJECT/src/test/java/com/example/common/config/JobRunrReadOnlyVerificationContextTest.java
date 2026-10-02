package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JobRunrReadOnlyVerificationContextTest {

    private static final String READ_ONLY_PROFILE = "local-readonly-verification";

    @ParameterizedTest(name = "readonly custom storage is absent, dev companion = {0}")
    @ValueSource(booleans = { false, true })
    void readonlyProfileDoesNotInvokeSqlFactoryOpenConnectionsOrCreateFallback(boolean withDevProfile) {
        DataSource dataSource = mock(DataSource.class);
        JobMapper jobMapper = mock(JobMapper.class);

        // A regression must fail without reaching JDBC or constructing real fallback storage.
        try (MockedStatic<SqlStorageProviderFactory> sqlFactory = mockStatic(SqlStorageProviderFactory.class);
                MockedConstruction<InMemoryStorageProvider> fallback = mockConstruction(InMemoryStorageProvider.class)) {
            sqlFactory.when(() -> SqlStorageProviderFactory.using(dataSource))
                    .thenThrow(new IllegalStateException("SQL storage factory must not run in readonly verification"));

            storageRunner(dataSource, jobMapper)
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles(
                            withDevProfile ? new String[] { "dev", READ_ONLY_PROFILE }
                                    : new String[] { READ_ONLY_PROFILE }))
                    .withPropertyValues(
                            "jobrunr.job-scheduler.enabled=false",
                            "jobrunr.background-job-server.enabled=false",
                            "jobrunr.dashboard.enabled=false",
                            "jobrunr.database.skip-create=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertAll(
                                () -> sqlFactory.verifyNoInteractions(),
                                () -> verify(dataSource, never()).getConnection(),
                                () -> verify(dataSource, never()).getConnection(anyString(), anyString()),
                                () -> verifyNoInteractions(dataSource, jobMapper),
                                () -> assertThat(fallback.constructed()).isEmpty(),
                                () -> assertThat(context).doesNotHaveBean(StorageProvider.class),
                                () -> assertThat(context).doesNotHaveBean(InMemoryStorageProvider.class));
                    });
        }
    }

    @Test
    void normalProfileStillCreatesConfiguredSqlStorageProvider() {
        DataSource dataSource = mock(DataSource.class);
        JobMapper jobMapper = mock(JobMapper.class);
        StorageProvider storageProvider = mock(StorageProvider.class);

        try (MockedStatic<SqlStorageProviderFactory> sqlFactory = mockStatic(SqlStorageProviderFactory.class);
                MockedConstruction<InMemoryStorageProvider> fallback = mockConstruction(InMemoryStorageProvider.class)) {
            sqlFactory.when(() -> SqlStorageProviderFactory.using(dataSource)).thenReturn(storageProvider);

            storageRunner(dataSource, jobMapper)
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(StorageProvider.class);
                        assertThat(context.getBean(StorageProvider.class)).isSameAs(storageProvider);
                        sqlFactory.verify(() -> SqlStorageProviderFactory.using(dataSource));
                        verify(storageProvider).setJobMapper(jobMapper);
                        verifyNoInteractions(dataSource, jobMapper);
                        assertThat(fallback.constructed()).isEmpty();
                    });
        }
    }

    private ApplicationContextRunner storageRunner(DataSource dataSource, JobMapper jobMapper) {
        // Deliberately isolate the existing custom configuration; starter exclusions get a separate test.
        return new ApplicationContextRunner()
                .withUserConfiguration(JobRunrConfig.class)
                .withBean(DataSource.class, () -> dataSource)
                .withBean(JobMapper.class, () -> jobMapper);
    }
}
