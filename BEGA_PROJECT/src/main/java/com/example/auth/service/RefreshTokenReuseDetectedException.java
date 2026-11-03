package com.example.auth.service;

import com.example.common.exception.BadRequestBusinessException;

public final class RefreshTokenReuseDetectedException extends BadRequestBusinessException {

    public RefreshTokenReuseDetectedException(String message) {
        super("REFRESH_TOKEN_REUSE_DETECTED", message);
    }
}
