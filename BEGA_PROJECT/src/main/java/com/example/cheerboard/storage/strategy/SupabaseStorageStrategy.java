package com.example.cheerboard.storage.strategy;

import com.example.cheerboard.storage.client.SupabaseStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class SupabaseStorageStrategy implements StorageStrategy {

    private final SupabaseStorageClient client;
    private final String defaultBucket;

    @Override
    public Mono<String> upload(MultipartFile file, String bucket, String path) {
        return Mono.fromCallable(() -> {
            try {
                return file.getBytes();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file bytes", e);
            }
        }).flatMap(bytes -> uploadBytes(bytes, file.getContentType(), bucket != null ? bucket : defaultBucket, path));
    }

    @Override
    public Mono<String> uploadBytes(byte[] bytes, String contentType, String bucket, String path) {
        String targetBucket = bucket != null ? bucket : defaultBucket;
        return client.upload(bytes, targetBucket, path, contentType);
    }

    @Override
    public Mono<Void> delete(String bucket, String path) {
        String targetBucket = bucket != null ? bucket : defaultBucket;
        return client.delete(targetBucket, path);
    }

    @Override
    public Mono<String> getUrl(String bucket, String path, int expiresInSeconds) {
        // Supabase Public URL (expiresInSeconds is ignored for public buckets)
        // If private buckets are used, Signed URL logic would be needed here.
        // Assuming public buckets for now as per valid use cases.
        String targetBucket = bucket != null ? bucket : defaultBucket;
        return Mono.just(client.getPublicUrl(targetBucket, path));
    }
}
