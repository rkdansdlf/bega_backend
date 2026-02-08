package com.example.cheerboard.storage.strategy;

import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

/**
 * 스토리지 전략 인터페이스 (OCI vs Local)
 */
public interface StorageStrategy {

    /**
     * 파일 업로드
     * 
     * @param file   업로드할 파일
     * @param bucket 버킷명 (로컬 저장소의 경우 최상위 디렉토리명으로 사용)
     * @param path   저장 경로 (예: posts/123/image.jpg)
     * @return 저장된 경로 (path와 동일하거나 조정될 수 있음)
     */
    Mono<String> upload(MultipartFile file, String bucket, String path);

    /**
     * 바이트 배열 업로드
     * 
     * @param bytes       업로드할 데이터
     * @param contentType MIME 타입
     * @param bucket      버킷명
     * @param path        저장 경로
     * @return 저장된 경로
     */
    Mono<String> uploadBytes(byte[] bytes, String contentType, String bucket, String path);

    /**
     * 파일 삭제
     * 
     * @param bucket 버킷명
     * @param path   파일 경로
     */
    Mono<Void> delete(String bucket, String path);

    /**
     * 파일 접근 URL 생성
     * 
     * @param bucket           버킷명
     * @param path             파일 경로
     * @param expiresInSeconds 유효 시간 (초) - 로컬 저장소의 경우 무시될 수 있음
     * @return 접근 가능한 URL (Signed URL or Public URL)
     */
    Mono<String> getUrl(String bucket, String path, int expiresInSeconds);
}
