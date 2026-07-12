package com.example.homepage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.cache.BoundedLocalCache;
import com.example.common.concurrent.StripedLockRegistry;
import com.example.prediction.PredictionBootstrapService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class HomeCacheBoundedStateTest {

    @Test
    void localFallbackStateAndKeyLocksUseBoundedStructures() throws Exception {
        assertFieldType(HomeBootstrapCacheService.class, "keyLocks", StripedLockRegistry.class);
        assertFieldType(HomeBootstrapCacheService.class, "fallbackBootstraps", BoundedLocalCache.class);
        assertFieldType(HomeBootstrapCacheService.class, "staleBootstraps", BoundedLocalCache.class);
        assertFieldType(HomeWidgetsCacheService.class, "keyLocks", StripedLockRegistry.class);
        assertFieldType(HomeWidgetsCacheService.class, "fallbackWidgets", BoundedLocalCache.class);
        assertFieldType(HomeRankingSnapshotCacheService.class, "keyLocks", StripedLockRegistry.class);
        assertFieldType(HomeRankingSnapshotCacheService.class, "fallbackSnapshots", BoundedLocalCache.class);
        assertFieldType(PredictionBootstrapService.class, "manualDataFailureCache", BoundedLocalCache.class);
    }

    private void assertFieldType(Class<?> owner, String fieldName, Class<?> expectedType) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        assertThat(field.getType()).isEqualTo(expectedType);
    }
}
