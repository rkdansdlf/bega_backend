package com.example.common.readonly;

import com.example.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ReadOnlyVerificationUnavailableException extends BusinessException {

    public static final String CODE = "READ_ONLY_VERIFICATION_UNAVAILABLE";

    public ReadOnlyVerificationUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, "Operation unavailable in read-only verification.");
    }
}
