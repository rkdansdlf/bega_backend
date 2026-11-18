package com.example.cheerboard.storage.strategy;

import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves a logical storage path exactly as the S3-compatible strategy does. */
public final class StorageObjectKey {
    private StorageObjectKey() {}

    public static String resolve(String bucket, String path) {
        String normalizedPath = path == null ? "" : path.strip();
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (bucket == null || bucket.isBlank()) {
            return normalizedPath;
        }
        String prefix = bucket + "/";
        return normalizedPath.startsWith(prefix) ? normalizedPath : prefix + normalizedPath;
    }

    /** Compares the exact resolved object key, including any bucket-prefix alias. */
    public static boolean isWithinResolvedPrefix(String bucket, String path, String prefix) {
        return resolve(bucket, path).startsWith(resolve(bucket, prefix));
    }

    /** Candidate spellings accepted by resolve for the same bucket-prefixed key. */
    public static Set<String> aliases(String bucket, String path) {
        String physicalKey = resolve(bucket, path);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(path == null ? "" : path.strip());
        candidates.add(physicalKey);
        candidates.add("/" + physicalKey);
        if (bucket != null && !bucket.isBlank() && physicalKey.startsWith(bucket + "/")) {
            String relative = physicalKey.substring(bucket.length() + 1);
            candidates.add(relative);
            candidates.add("/" + relative);
        }
        candidates.removeIf(candidate -> !resolve(bucket, candidate).equals(physicalKey));
        return Set.copyOf(candidates);
    }
}
