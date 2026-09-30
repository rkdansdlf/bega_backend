package com.example.cheerboard.storage.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 실제 어댑터 메서드(exists/head/download/upload/deleteChecked)가 실패를 올바르게 분류해 던지는지 검증한다. */
class S3StorageStrategyTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3StorageStrategy strategy = new S3StorageStrategy(s3Client, mock(S3Presigner.class), "bucket");

    private static S3Exception s3Error(int status) {
        return (S3Exception) S3Exception.builder().statusCode(status).message("status " + status).build();
    }

    private static StorageOperationException.Kind kindOf(Runnable call) {
        return assertThrows(StorageOperationException.class, call::run).getKind();
    }

    @Test
    @DisplayName("HEAD 200이면 exists=true, 메타데이터를 반환한다")
    void headSuccess() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(3L).contentType("image/png").build());

        assertTrue(strategy.exists("b", "k").block());
        assertEquals(3L, strategy.head("b", "k").block().contentLength());
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
    @DisplayName("429/5xx/네트워크 오류는 객체 없음이 아니라 TRANSIENT StorageOperationException이다")
    void transientFailuresAreNotMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(s3Error(503))
                .thenThrow(s3Error(429))
                .thenThrow(SdkClientException.create("connection reset"))
                .thenThrow(s3Error(503));

        assertEquals(StorageOperationException.Kind.TRANSIENT, kindOf(() -> strategy.exists("b", "k").block()));
        assertEquals(StorageOperationException.Kind.TRANSIENT, kindOf(() -> strategy.exists("b", "k").block()));
        assertEquals(StorageOperationException.Kind.TRANSIENT, kindOf(() -> strategy.exists("b", "k").block()));
        assertEquals(StorageOperationException.Kind.TRANSIENT, kindOf(() -> strategy.head("b", "k").block()));
    }

    @Test
    @DisplayName("403 같은 영구 실패는 PERMANENT로 분류한다 (재시도 안내 대상이 아니다)")
    void authorizationFailureIsPermanent() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Error(403));

        assertEquals(StorageOperationException.Kind.PERMANENT, kindOf(() -> strategy.exists("b", "k").block()));
    }

    @Test
    @DisplayName("download은 404를 OBJECT_NOT_FOUND, 5xx를 TRANSIENT로 알린다")
    void downloadClassifiesFailures() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("nope").build())
                .thenThrow(s3Error(500));

        assertEquals(StorageOperationException.Kind.OBJECT_NOT_FOUND, kindOf(() -> strategy.download("b", "k").block()));
        assertEquals(StorageOperationException.Kind.TRANSIENT, kindOf(() -> strategy.download("b", "k").block()));
    }

    @Test
    @DisplayName("업로드 실패도 분류해 던진다 — 일반 RuntimeException으로 묻히면 정상 staging 객체가 정리된다")
    void uploadFailureIsClassified() {
        when(s3Client.putObject(any(PutObjectRequest.class), ArgumentMatchers.<RequestBody>any()))
                .thenThrow(s3Error(503));

        assertEquals(StorageOperationException.Kind.TRANSIENT,
                kindOf(() -> strategy.uploadBytes(new byte[] {1}, "image/png", "b", "k").block()));
    }

    @Test
    @DisplayName("deleteChecked: 성공과 404(이미 없음)는 성공, 5xx는 TRANSIENT 예외")
    void deleteCheckedReportsFailures() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build())
                .thenThrow(NoSuchKeyException.builder().message("nope").build())
                .thenThrow(s3Error(503));

        strategy.deleteChecked("b", "k").block();
        strategy.deleteChecked("b", "k").block();
        assertEquals(StorageOperationException.Kind.TRANSIENT, kindOf(() -> strategy.deleteChecked("b", "k").block()));
    }

    @Test
    @DisplayName("기존 delete는 하위 호환을 위해 실패를 삼킨다 (DB 상태 기록이 필요한 호출자는 deleteChecked를 쓴다)")
    void legacyDeleteStillSwallowsFailures() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Error(503));

        strategy.delete("b", "k").block(); // 예외 없이 끝난다
    }
}
