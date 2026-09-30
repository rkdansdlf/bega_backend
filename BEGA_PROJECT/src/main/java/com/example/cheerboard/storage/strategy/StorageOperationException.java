package com.example.cheerboard.storage.strategy;

/**
 * 스토리지 어댑터가 호출자에게 돌려주는 실패 계약.
 *
 * <p>호출자는 "객체가 없다"와 "지금은 알 수 없다"를 구분해야 한다. 일시 장애(5xx, timeout)를
 * 객체 부재로 오판해 DB 상태를 삭제로 바꾸면 정상 객체가 유실된 것처럼 보이기 때문이다.
 */
public class StorageOperationException extends RuntimeException {

    public enum Kind {
        /** 객체가 실제로 존재하지 않는다(404). 호출자는 정리(cleanup)를 진행해도 된다. */
        OBJECT_NOT_FOUND,
        /** 재시도하면 성공할 수 있다(5xx, 429, timeout, 연결 실패). 상태를 바꾸지 말 것. */
        TRANSIENT,
        /** 재시도해도 소용없다(권한, 잘못된 요청 등). 운영자 개입이 필요하다. */
        PERMANENT
    }

    private final Kind kind;

    public StorageOperationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isObjectNotFound() {
        return kind == Kind.OBJECT_NOT_FOUND;
    }
}
