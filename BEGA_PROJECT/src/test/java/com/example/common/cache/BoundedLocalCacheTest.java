package com.example.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoundedLocalCacheTest {

    @Test
    void evictsLeastRecentlyUsedEntryAtMaximumSize() {
        BoundedLocalCache<String, String> cache = new BoundedLocalCache<>(2);
        cache.put("a", "A");
        cache.put("b", "B");
        assertThat(cache.get("a")).isEqualTo("A");

        cache.put("c", "C");

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("a")).isEqualTo("A");
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("c")).isEqualTo("C");
    }

    @Test
    void conditionalRemoveOnlyDeletesMatchingValue() {
        BoundedLocalCache<String, String> cache = new BoundedLocalCache<>(2);
        cache.put("key", "new");

        assertThat(cache.remove("key", "old")).isFalse();
        assertThat(cache.get("key")).isEqualTo("new");
        assertThat(cache.remove("key", "new")).isTrue();
        assertThat(cache.get("key")).isNull();
    }
}
