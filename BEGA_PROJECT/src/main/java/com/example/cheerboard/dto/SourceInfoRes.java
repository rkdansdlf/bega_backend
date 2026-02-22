package com.example.cheerboard.dto;

public record SourceInfoRes(
        String title,
        String author,
        String url,
        String license,
        String licenseUrl,
        String changedNote,
        String snapshotType) {
}
