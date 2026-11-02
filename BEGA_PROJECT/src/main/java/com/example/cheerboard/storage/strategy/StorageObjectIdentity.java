package com.example.cheerboard.storage.strategy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Physical bucket and key; callers must compare both fields even when their digest matches. */
public record StorageObjectIdentity(String bucket, String key) {
    public StorageObjectIdentity {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(key, "key");
        if (bucket.isBlank() || key.isBlank()) throw new IllegalArgumentException("A physical bucket and key are required");
    }

    public static StorageObjectIdentity of(String physicalBucket, String logicalBucket, String path) {
        String checked = path == null ? "" : path.strip();
        if (checked.startsWith("//")) throw new IllegalArgumentException("An ambiguous storage path cannot be cleaned automatically");
        if (checked.startsWith("/")) checked = checked.substring(1);
        if (checked.isBlank() || checked.chars().anyMatch(Character::isISOControl)
                || checked.indexOf('?') >= 0 || checked.indexOf('#') >= 0 || checked.indexOf('%') >= 0
                || checked.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            throw new IllegalArgumentException("A non-URL storage path is required");
        }
        return new StorageObjectIdentity(physicalBucket, StorageObjectKey.resolve(logicalBucket, path));
    }

    public String digest() {
        byte[] bucketBytes = bucket.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(ByteBuffer.allocate(Integer.BYTES).putInt(bucketBytes.length).array());
            sha256.update(bucketBytes);
            sha256.update(keyBytes);
            return HexFormat.of().formatHex(sha256.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
