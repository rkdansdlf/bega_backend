package com.example.media.entity;

import com.example.cheerboard.storage.config.StorageConfig;
import java.util.Locale;

public enum MediaDomain {
    PROFILE("profile"),
    DIARY("diary"),
    CHEER("cheer"),
    CHAT("chat");

    private final String pathSegment;

    MediaDomain(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String getPathSegment() {
        return pathSegment;
    }

    public String buildStagingObjectKey(Long userId, Long assetId, String fileName) {
        return String.format(
                Locale.ROOT,
                "media/staging/%s/%d/%d-%s",
                pathSegment,
                userId,
                assetId,
                sanitizeFileName(fileName));
    }

    public String buildFinalObjectKey(Long userId, Long assetId, String extension) {
        return String.format(Locale.ROOT, "media/%s/%d/%d.%s", pathSegment, userId, assetId, sanitizeExtension(extension));
    }

    public String buildProfileFeedObjectKey(Long userId, Long assetId, String extension) {
        if (this != PROFILE) {
            throw new IllegalStateException("PROFILE 도메인에서만 feed derivative key를 생성할 수 있습니다.");
        }
        return String.format(Locale.ROOT, "media/profile-feed/%d/%d.%s", userId, assetId, sanitizeExtension(extension));
    }

    public String resolveBucket(StorageConfig storageConfig) {
        return switch (this) {
            case PROFILE -> storageConfig.getProfileBucket();
            case DIARY -> storageConfig.getDiaryBucket();
            case CHEER, CHAT -> storageConfig.getCheerBucket();
        };
    }

    public boolean isManagedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.strip();
        if (this == PROFILE) {
            return normalized.startsWith("media/profile/") || normalized.startsWith("media/profile-feed/");
        }
        return normalized.startsWith("media/" + pathSegment + "/");
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName == null || fileName.isBlank() ? "upload.bin" : fileName.strip();
        return normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "bin";
        }
        return extension.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
