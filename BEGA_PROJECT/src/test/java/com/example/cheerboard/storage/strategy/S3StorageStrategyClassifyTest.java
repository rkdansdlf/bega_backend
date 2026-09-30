package com.example.cheerboard.storage.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3StorageStrategyClassifyTest {

    private static S3Exception s3(int status) {
        return (S3Exception) S3Exception.builder().statusCode(status).message("s3 " + status).build();
    }

    @Test
    @DisplayName("404/NoSuchKey만 객체 부재로 분류한다")
    void notFound() {
        assertEquals(StorageOperationException.Kind.OBJECT_NOT_FOUND,
                S3StorageStrategy.classify(NoSuchKeyException.builder().message("x").build()));
        assertEquals(StorageOperationException.Kind.OBJECT_NOT_FOUND, S3StorageStrategy.classify(s3(404)));
    }

    @Test
    @DisplayName("5xx/429/408/timeout·연결 실패는 일시 장애로 분류한다")
    void transientFailures() {
        for (int status : new int[] {500, 502, 503, 429, 408}) {
            assertEquals(StorageOperationException.Kind.TRANSIENT, S3StorageStrategy.classify(s3(status)), "status " + status);
        }
        assertEquals(StorageOperationException.Kind.TRANSIENT,
                S3StorageStrategy.classify(SdkClientException.create("connect timeout")));
        assertEquals(StorageOperationException.Kind.TRANSIENT, S3StorageStrategy.classify(new IllegalStateException("?")));
    }

    @Test
    @DisplayName("403/400 등 나머지 4xx는 영구 실패로 분류한다")
    void permanentFailures() {
        assertEquals(StorageOperationException.Kind.PERMANENT, S3StorageStrategy.classify(s3(403)));
        assertEquals(StorageOperationException.Kind.PERMANENT, S3StorageStrategy.classify(s3(400)));
    }
}
