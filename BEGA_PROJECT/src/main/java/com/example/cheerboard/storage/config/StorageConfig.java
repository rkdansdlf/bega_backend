package com.example.cheerboard.storage.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Supabase Storage 설정 클래스
 * - WebClient Bean 등록
 * - 환경변수 매핑
 */
@Slf4j
@Configuration
@Getter
public class StorageConfig {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.buckets.cheer}")
    private String cheerBucket;

    @Value("${supabase.storage.buckets.diary}")
    private String diaryBucket;

    @Value("${supabase.storage.signed-url-ttl-seconds}")
    private Integer signedUrlTtlSeconds;

    @Value("${supabase.storage.max-image-bytes}")
    private Long maxImageBytes;

    @Value("${supabase.storage.max-images-per-post}")
    private Integer maxImagesPerPost;

    @Value("${supabase.storage.max-images-per-diary}")
    private Integer maxImagesPerDiary;
    
    @Value("${supabase.storage.buckets.profile}")
    private String profileBucket;  

    @Value("${supabase.storage.max-images-per-profile:1}")
    private Integer maxImagesPerProfile;  
    
    @PostConstruct
    public void logConfig() {
        log.info("=== Supabase Storage 설정 ===");
        log.info("URL: {}", supabaseUrl);
        log.info("Service Role Key: {}", serviceRoleKey != null && !serviceRoleKey.isEmpty()
            ? "설정됨 (길이: " + serviceRoleKey.length() + ")" : "설정되지 않음");
        log.info("Bucket: [cheer={}, diary={}, profile={}]", cheerBucket, diaryBucket, profileBucket);
        log.info("Max Images Per Post: {}", maxImagesPerPost);
        log.info("Max Images Per Profile: {}", maxImagesPerProfile); 
        log.info("Max Image Bytes: {} MB", maxImageBytes / 1024 / 1024);

        if (serviceRoleKey == null || serviceRoleKey.isEmpty()) {
            log.error("경고: SUPABASE_SERVICE_ROLE_KEY가 설정되지 않았습니다! 이미지 업로드가 작동하지 않습니다.");
        }
    }

    /**
     * Supabase Storage API용 WebClient
     */
    @Bean
    public WebClient supabaseStorageWebClient() {
        log.info("Supabase Storage WebClient 생성: baseUrl={}", supabaseUrl + "/storage/v1");
        return WebClient.builder()
            .baseUrl(supabaseUrl + "/storage/v1")
            .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
            .defaultHeader("apikey", serviceRoleKey)
            .build();
    }
}
