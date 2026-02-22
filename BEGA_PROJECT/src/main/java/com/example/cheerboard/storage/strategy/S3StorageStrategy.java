package com.example.cheerboard.storage.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * OCI Object Storage (S3 Compatible) Strategy
 */
@Slf4j
@RequiredArgsConstructor
public class S3StorageStrategy implements StorageStrategy {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    /**
     * path가 이미 "bucket/..." 형태인 경우 중복 prefix를 방지한다.
     */
    private String buildObjectKey(String bucket, String path) {
        String normalizedPath = path == null ? "" : path.strip();
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        if (bucket == null || bucket.isBlank()) {
            return normalizedPath;
        }

        String prefix = bucket + "/";
        if (normalizedPath.startsWith(prefix)) {
            return normalizedPath;
        }
        return prefix + normalizedPath;
    }

    @Override
    public Mono<String> upload(org.springframework.web.multipart.MultipartFile file, String bucket, String path) {
        return Mono.fromCallable(() -> {
            try {
                // OCI/S3는 bucketName을 설정 시점에 고정하거나, 메서드 인자를 무시하고 설정된 버킷을 사용할 수 있음
                // 여기서는 인자로 전달된 bucket 파라미터를 하위 경로(prefix)로 사용하여 단일 버킷 구조로 통합할 수도 있지만,
                // 스토리지 구조 유지를 위해 path에 포함하거나 메타데이터로 활용.
                // *중요*: OCI 버킷은 보통 하나로 관리하므로, bucket param은 무시하고 config의 bucketName을 사용하되
                // path 앞에 prefix로 붙이는 것이 안전함.

                String objectKey = buildObjectKey(bucket, path);

                PutObjectRequest putOb = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .build();

                s3Client.putObject(putOb, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
                return objectKey;
            } catch (Exception e) {
                throw new RuntimeException("S3 업로드 실패", e);
            }
        });
    }

    @Override
    public Mono<String> uploadBytes(byte[] bytes, String contentType, String bucket, String path) {
        return Mono.fromCallable(() -> {
            try {
                String objectKey = buildObjectKey(bucket, path);

                PutObjectRequest putOb = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putOb, RequestBody.fromBytes(bytes));
                return objectKey;
            } catch (Exception e) {
                throw new RuntimeException("S3 업로드 실패", e);
            }
        });
    }

    @Override
    public Mono<Void> delete(String bucket, String path) {
        return Mono.fromRunnable(() -> {
            try {
                String objectKey = buildObjectKey(bucket, path);
                DeleteObjectRequest deleteOb = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();

                s3Client.deleteObject(deleteOb);
            } catch (Exception e) {
                log.error("S3 삭제 실패: {}", path, e);
            }
        });
    }

    @Override
    public Mono<String> getUrl(String bucket, String path, int expiresInSeconds) {
        return Mono.fromCallable(() -> {
            try {
                String objectKey = buildObjectKey(bucket, path);

                // Signed URL 생성
                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                        .getObjectRequest(b -> b.bucket(bucketName).key(objectKey))
                        .build();

                return s3Presigner.presignGetObject(presignRequest).url().toString();
            } catch (Exception e) {
                log.error("S3 URL 생성 실패: {}", path, e);
                return null;
            }
        });
    }
}
