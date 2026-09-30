package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobRunrConfigStoragePolicyTest {

    private static StorageProvider sqlFailure() {
        throw new IllegalStateException("sql storage init failed");
    }

    @Test
    @DisplayName("SQL provider 초기화가 실패하면 기본 정책은 기동 실패다 (조용한 In-memory 폴백 금지)")
    void sqlFailureFailsFastByDefault() {
        assertThatThrownBy(() -> JobRunrConfig.createStorageProvider(
                        JobRunrConfigStoragePolicyTest::sqlFailure,
                        InMemoryStorageProvider::new,
                        false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sql storage init failed");
    }

    @Test
    @DisplayName("명시적으로 허용한 경우에만 In-memory 로 폴백한다")
    void explicitOptInFallsBackToInMemory() {
        StorageProvider provider = JobRunrConfig.createStorageProvider(
                JobRunrConfigStoragePolicyTest::sqlFailure, InMemoryStorageProvider::new, true);

        assertThat(provider).isInstanceOf(InMemoryStorageProvider.class);
    }

    @Test
    @DisplayName("SQL provider 가 정상이면 폴백 설정과 무관하게 SQL provider 를 쓴다")
    void healthySqlProviderIsAlwaysUsed() {
        StorageProvider sql = new InMemoryStorageProvider(); // stand-in for a healthy SQL provider
        StorageProvider fallback = new InMemoryStorageProvider();

        assertThat(JobRunrConfig.createStorageProvider(() -> sql, () -> fallback, true)).isSameAs(sql);
        assertThat(JobRunrConfig.createStorageProvider(() -> sql, () -> fallback, false)).isSameAs(sql);
    }

    @Test
    @DisplayName("prod 프로필에서 In-memory 폴백을 켜면 기동이 거부된다")
    void prodRejectsInMemoryFallbackFlag() {
        assertThatThrownBy(() -> JobRunrConfig.assertFallbackPolicy(new String[] {"prod"}, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        assertThatThrownBy(() -> JobRunrConfig.assertFallbackPolicy(new String[] {"dev", "PROD"}, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prod 가 아닌 프로필의 명시적 옵트인, 그리고 prod 의 기본값(false)은 통과한다")
    void nonProdOptInAndProdDefaultAreAllowed() {
        assertThatCode(() -> JobRunrConfig.assertFallbackPolicy(new String[] {"local"}, true)).doesNotThrowAnyException();
        assertThatCode(() -> JobRunrConfig.assertFallbackPolicy(new String[] {"prod"}, false)).doesNotThrowAnyException();
        assertThatCode(() -> JobRunrConfig.assertFallbackPolicy(new String[] {}, false)).doesNotThrowAnyException();
    }
}
