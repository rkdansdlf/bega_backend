package com.example.cheerboard.storage.client;

import com.example.cheerboard.storage.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Supabase Storage API 클라이언트
 * - 파일 업로드, 삭제, 서명 URL 발급
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupabaseStorageClient {

    private final WebClient supabaseStorageWebClient;
    private final StorageConfig config;

    /**
     * 파일 업로드
     * @param file 업로드할 파일
     * @param storagePath 저장 경로 (예: posts/123/uuid.jpg)
     * @return 업로드된 파일 정보
     */
    public Mono<UploadResponse> upload(MultipartFile file, String storagePath) {
        try {
            byte[] bytes = file.getBytes();

            return supabaseStorageWebClient
                .post()
                .uri("/object/" + config.getBucket() + "/" + storagePath)
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .bodyValue(bytes)
                .retrieve()
                .onStatus(
                    status -> !status.is2xxSuccessful(),
                    response -> response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Supabase Storage upload failed: status={}, body={}",
                                response.statusCode(), body);
                            return Mono.error(new RuntimeException(
                                "파일 업로드에 실패했습니다: " + body));
                        })
                )
                .bodyToMono(UploadResponse.class)
                .doOnSuccess(res -> log.info("파일 업로드 성공: path={}", storagePath))
                .doOnError(err -> log.error("파일 업로드 실패: path={}, error={}",
                    storagePath, err.getMessage()));

        } catch (Exception e) {
            log.error("파일 업로드 중 오류 발생: path={}", storagePath, e);
            return Mono.error(new RuntimeException("파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 파일 삭제
     * @param storagePath 삭제할 파일 경로
     */
    public Mono<Void> delete(String storagePath) {
        return supabaseStorageWebClient
            .delete()
            .uri("/object/{bucket}/{path}", config.getBucket(), storagePath)
            .retrieve()
            .onStatus(
                status -> !status.is2xxSuccessful(),
                response -> response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Supabase Storage delete failed: status={}, body={}",
                            response.statusCode(), body);
                        return Mono.error(new RuntimeException(
                            "파일 삭제에 실패했습니다: " + body));
                    })
            )
            .bodyToMono(Void.class)
            .doOnSuccess(v -> log.info("파일 삭제 성공: path={}", storagePath))
            .doOnError(err -> log.error("파일 삭제 실패: path={}, error={}",
                storagePath, err.getMessage()));
    }

    /**
     * 서명된 URL 생성 (읽기 전용)
     * @param storagePath 파일 경로
     * @param expiresIn 만료 시간 (초)
     * @return 서명된 URL
     */
    public Mono<SignedUrlResponse> createSignedUrl(String storagePath, int expiresIn) {
        String requestBody = String.format("{\"expiresIn\": %d}", expiresIn);

        return supabaseStorageWebClient
            .post()
            .uri("/object/sign/" + config.getBucket() + "/" + storagePath)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(
                status -> !status.is2xxSuccessful(),
                response -> response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Supabase Storage signed URL failed: status={}, body={}",
                            response.statusCode(), body);
                        return Mono.error(new RuntimeException(
                            "서명 URL 생성에 실패했습니다: " + body));
                    })
            )
            .bodyToMono(SignedUrlResponse.class)
            .doOnSuccess(res -> log.info("Supabase 응답 signedURL={}", res != null ? res.signedUrl() : "null"))
            .map(res -> {
                if (res == null || res.signedUrl() == null) {
                    log.warn("signedUrl이 null입니다");
                    return new SignedUrlResponse(null);
                }
                String base = config.getSupabaseUrl().replaceAll("/$", "");
                // Supabase가 내려준 문자열 그대로 붙여줍니다 (JWT 서명 보존)
                // /storage/v1 경로를 추가해야 올바른 URL이 됩니다
                String fullUrl = base + "/storage/v1" + res.signedUrl();
                log.info("최종 URL 생성: {}", fullUrl);
                return new SignedUrlResponse(fullUrl);
            })
            .doOnSuccess(res -> log.debug("서명 URL 생성 성공: path={}, url={}", storagePath, res.signedUrl()))
            .doOnError(err -> log.error("서명 URL 생성 실패: path={}, error={}",
                storagePath, err.getMessage()));
    }

    /**
     * 파일 이동 (썸네일 지정 시 사용)
     * @param fromPath 원본 경로
     * @param toPath 대상 경로
     */
    public Mono<Void> move(String fromPath, String toPath) {
        String requestBody = String.format(
            "{\"bucketId\": \"%s\", \"sourceKey\": \"%s\", \"destinationKey\": \"%s\"}",
            config.getBucket(), fromPath, toPath
        );

        return supabaseStorageWebClient
            .post()
            .uri("/object/move")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(
                status -> !status.is2xxSuccessful(),
                response -> response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Supabase Storage move failed: status={}, body={}",
                            response.statusCode(), body);
                        return Mono.error(new RuntimeException(
                            "파일 이동에 실패했습니다: " + body));
                    })
            )
            .bodyToMono(Void.class)
            .doOnSuccess(v -> log.info("파일 이동 성공: from={}, to={}", fromPath, toPath))
            .doOnError(err -> log.error("파일 이동 실패: from={}, to={}, error={}",
                fromPath, toPath, err.getMessage()));
    }

    /**
     * 업로드 응답 DTO
     */
    public record UploadResponse(
        String Key,
        String Id,
        String Bucket
    ) {}

    /**
     * 서명 URL 응답 DTO
     */
    public record SignedUrlResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("signedURL")
        String signedUrl
    ) {}
}
