package com.example.cheerboard.storage.strategy;

import java.util.Map;

public record PresignedUpload(
        String url,
        Map<String, String> requiredHeaders) {
}
