package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableBusinessException extends BusinessException {

    public ServiceUnavailableBusinessException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
