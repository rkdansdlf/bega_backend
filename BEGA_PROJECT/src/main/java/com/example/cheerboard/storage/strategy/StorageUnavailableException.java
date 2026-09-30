package com.example.cheerboard.storage.strategy;

/**
 * 스토리지 조회가 "객체 없음"이 아닌 이유(타임아웃, 429, 5xx, 네트워크 오류 등)로 실패했음을 나타낸다.
 * 객체가 없다는 사실과 구분되어야 호출자가 일시 장애를 삭제/유실로 오판하지 않는다.
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
