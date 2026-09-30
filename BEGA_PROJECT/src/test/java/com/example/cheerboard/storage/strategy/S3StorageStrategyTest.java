package com.example.cheerboard.storage.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3StorageStrategyTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3StorageStrategy strategy = new S3StorageStrategy(s3Client, mock(S3Presigner.class), "bucket");

    private static S3Exception s3Error(int status) {
        return (S3Exception) S3Exception.builder().statusCode(status).message("status " + status).build();
    }

    @Test
    @DisplayName("HEAD 200이면 exists=true, 메타데이터를 반환한다")
    void headSuccess() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(3L).contentType("image/png").build());

        assertTrue(strategy.exists("b", "k").block());
        assertTrue(strategy.head("b", "k").block().contentLength() == 3L);
    }

    @Test
    @DisplayName("NoSuchKey와 404는 객체 없음으로 처리한다")
    void notFoundIsMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("nope").build())
                .thenThrow(s3Error(404))
                .thenThrow(NoSuchKeyException.builder().message("nope").build())
                .thenThrow(s3Error(404));

        assertFalse(strategy.exists("b", "k").block());
        assertFalse(strategy.exists("b", "k").block());
        assertNull(strategy.head("b", "k").block());
        assertNull(strategy.head("b", "k").block());
    }

    @Test
    @DisplayName("429/5xx/네트워크 오류는 객체 없음이 아니라 StorageUnavailableException이다")
    void transientFailuresAreNotMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(s3Error(503))
                .thenThrow(s3Error(429))
                .thenThrow(SdkClientException.create("connection reset"))
                .thenThrow(s3Error(503));

        assertThrows(StorageUnavailableException.class, () -> strategy.exists("b", "k").block());
        assertThrows(StorageUnavailableException.class, () -> strategy.exists("b", "k").block());
        assertThrows(StorageUnavailableException.class, () -> strategy.exists("b", "k").block());
        assertThrows(StorageUnavailableException.class, () -> strategy.head("b", "k").block());
    }
}
