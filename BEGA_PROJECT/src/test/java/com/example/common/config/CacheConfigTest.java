package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

class CacheConfigTest {

    @Test
    @DisplayName("home bootstrap/widgets 캐시는 Caffeine L1에도 등록된다")
    void caffeineCacheManagerIncludesHomeCaches() {
        CacheConfig cacheConfig = new CacheConfig();

        CaffeineCacheManager manager = (CaffeineCacheManager) cacheConfig.caffeineCacheManager();

        assertThat(manager.getCacheNames())
                .contains(CacheConfig.HOME_BOOTSTRAP, CacheConfig.HOME_WIDGETS);
    }
}
