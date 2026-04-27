package com.example.media.exception;

import com.example.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class MediaQuotaExceededException extends BusinessException {

    public MediaQuotaExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "MEDIA_QUOTA_EXCEEDED", message);
    }
}
