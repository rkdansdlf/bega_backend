package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JobRunrConfigFallbackTest {

    private final JobRunrConfig config = new JobRunrConfig();
    private final JobMapper jobMapper = mock(JobMapper.class);

    private DataSource brokenDataSource() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));
        return dataSource;
    }

    private MockEnvironment environmentWith(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    @DisplayName("prod 에서 SQL StorageProvider 초기화가 실패하면 in-memory 로 강등하지 않고 기동을 실패시킨다")
    void prodFailsFastInsteadOfFallingBack() throws Exception {
        DataSource dataSource = brokenDataSource();

        assertThatThrownBy(() -> config.storageProvider(dataSource, jobMapper, environmentWith("prod")))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("dev-adb 에서도 in-memory fallback 은 허용되지 않는다")
    void devAdbFailsFast() throws Exception {
        DataSource dataSource = brokenDataSource();

        assertThatThrownBy(() -> config.storageProvider(dataSource, jobMapper, environmentWith("dev-adb")))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("local/dev 에서는 기존처럼 in-memory 로 fallback 한다")
    void nonProdFallsBackToInMemory() throws Exception {
        DataSource dataSource = brokenDataSource();

        StorageProvider provider = config.storageProvider(dataSource, jobMapper, environmentWith("local"));

        assertThat(provider).isInstanceOf(InMemoryStorageProvider.class);
    }
}
