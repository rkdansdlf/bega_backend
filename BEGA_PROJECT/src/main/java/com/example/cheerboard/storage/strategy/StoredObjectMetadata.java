package com.example.cheerboard.storage.strategy;

public record StoredObjectMetadata(
        Long contentLength,
        String contentType) {
}
