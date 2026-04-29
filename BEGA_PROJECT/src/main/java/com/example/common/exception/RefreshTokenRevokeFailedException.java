package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenRevokeFailedException extends BusinessException {

    public static final String CODE = "REFRESH_TOKEN_REVOKE_FAILED";
    public static final String MESSAGE = "보안 조치를 완료할 수 없습니다. 잠시 후 다시 시도해주세요.";

    public RefreshTokenRevokeFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, MESSAGE);
    }

    public RefreshTokenRevokeFailedException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, MESSAGE);
        if (cause != null) {
            initCause(cause);
        }
    }
}
