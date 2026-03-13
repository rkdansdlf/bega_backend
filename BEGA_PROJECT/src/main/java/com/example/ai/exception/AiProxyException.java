package com.example.ai.exception;

import com.example.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AiProxyException extends BusinessException {

    public AiProxyException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
