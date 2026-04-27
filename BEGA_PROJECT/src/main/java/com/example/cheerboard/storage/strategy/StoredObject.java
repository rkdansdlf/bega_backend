package com.example.cheerboard.storage.strategy;

public record StoredObject(
        byte[] bytes,
        String contentType) {

    public long size() {
        return bytes == null ? 0L : bytes.length;
    }
}
