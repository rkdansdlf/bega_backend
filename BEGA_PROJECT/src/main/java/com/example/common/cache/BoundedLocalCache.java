package com.example.common.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

public final class BoundedLocalCache<K, V> {

    private final Map<K, V> entries;

    public BoundedLocalCache(int maximumSize) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumSize;
            }
        };
    }

    public synchronized V get(K key) {
        return entries.get(Objects.requireNonNull(key, "key must not be null"));
    }

    public synchronized void put(K key, V value) {
        entries.put(
                Objects.requireNonNull(key, "key must not be null"),
                Objects.requireNonNull(value, "value must not be null"));
    }

    public synchronized V remove(K key) {
        return entries.remove(Objects.requireNonNull(key, "key must not be null"));
    }

    public synchronized boolean remove(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        V current = entries.get(key);
        if (!Objects.equals(current, value)) {
            return false;
        }
        entries.remove(key);
        return true;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void removeIf(BiPredicate<? super K, ? super V> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        entries.entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }
}
