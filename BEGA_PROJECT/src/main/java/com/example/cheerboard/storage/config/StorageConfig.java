package com.example.cheerboard.storage.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Storage 설정 클래스
 * - OCI Object Storage(S3 호환) 설정
 * - 스토리지 전략 Bean 등록
 */
@Slf4j
@Configuration
@Getter
public class StorageConfig {

    @Value("${storage.type:oci}")
    private String storageType;

    // OCI / S3 Config
    @Value("${oci.s3.access-key:}")
    private String s3AccessKey;
    @Value("${oci.s3.secret-key:}")
    private String s3SecretKey;
    @Value("${oci.s3.region:ap-seoul-1}")
    private String s3Region;
    @Value("${oci.s3.endpoint:}")
    private String s3Endpoint;
    @Value("${oci.s3.bucket:}")
    private String s3BucketName;

    // Supabase Config
    @Value("${supabase.url:}")
    private String supabaseUrl;
    @Value("${supabase.key:}")
    private String supabaseKey;
    @Value("${supabase.bucket:}")
    private String supabaseBucket;

    @PostConstruct
    public void logConfig() {
        log.info("=== Storage 설정 ===");
        log.info("Detected storage.type: '{}'", storageType);
        log.info("OCI S3 Endpoint: {}", s3Endpoint);
        log.info("OCI S3 Bucket: {}", s3BucketName);
        log.info("Supabase URL: {}", supabaseUrl);
        log.info("Supabase Bucket: {}", supabaseBucket);
    }

    @Bean
    public com.example.cheerboard.storage.strategy.StorageStrategy storageStrategy() {

        log.info("=== Storage Strategy Config ===");
        log.info("Detected storage.type: '{}'", storageType);

        if ("supabase".equalsIgnoreCase(storageType)) {
            log.info("SupabaseStorageStrategy 사용");
            return createSupabaseStrategy();
        }

        // 그 외(기본값 포함)는 무조건 OCI 사용
        log.info("S3StorageStrategy 사용 (OCI Object Storage) - Default");
        return createS3Strategy();
    }

    // --- Getters for Services and Validators ---
    public String getCheerBucket() {
        return s3BucketName;
    }

    public String getDiaryBucket() {
        return s3BucketName;
    }

    public String getProfileBucket() {
        return s3BucketName;
    }

    public int getSignedUrlTtlSeconds() {
        return 518400; // 6 days (S3/OCI SigV4 max is 7 days)
    }

    public long getMaxImageBytes() {
        return 5242880L;
    } // 5MB

    public int getMaxImagesPerPost() {
        return 10;
    }

    public int getMaxImagesPerDiary() {
        return 6;
    }

    public int getMaxImagesPerProfile() {
        return 1;
    }

    @Bean
    public S3Client s3Client() {
        if (s3AccessKey.isEmpty() || s3SecretKey.isEmpty() || s3Endpoint.isEmpty()) {
            return null; // or throw exception if mandatory
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3AccessKey, s3SecretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        java.net.URI endpointUri = java.net.URI.create(s3Endpoint);
        Region region = Region.of(s3Region);
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .region(region)
                .endpointOverride(endpointUri)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        if (s3AccessKey.isEmpty() || s3SecretKey.isEmpty() || s3Endpoint.isEmpty()) {
            return null;
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3AccessKey, s3SecretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        java.net.URI endpointUri = java.net.URI.create(s3Endpoint);
        Region region = Region.of(s3Region);
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Presigner.builder()
                .region(region)
                .endpointOverride(endpointUri)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    private com.example.cheerboard.storage.strategy.StorageStrategy createS3Strategy() {
        S3Client s3Client = s3Client();
        S3Presigner s3Presigner = s3Presigner();

        if (s3Client == null || s3Presigner == null) {
            throw new IllegalStateException("OCI S3 설정이 누락되었습니다. (access-key, secret-key, endpoint)");
        }

        return new com.example.cheerboard.storage.strategy.S3StorageStrategy(s3Client, s3Presigner, s3BucketName);
    }

    private com.example.cheerboard.storage.strategy.StorageStrategy createSupabaseStrategy() {
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty() || supabaseBucket.isEmpty()) {
            throw new IllegalStateException("Supabase 설정이 누락되었습니다. (url, key, bucket)");
        }
        com.example.cheerboard.storage.client.SupabaseStorageClient client = new com.example.cheerboard.storage.client.SupabaseStorageClient(
                supabaseUrl, supabaseKey);
        return new com.example.cheerboard.storage.strategy.SupabaseStorageStrategy(client, supabaseBucket);
    }
}
