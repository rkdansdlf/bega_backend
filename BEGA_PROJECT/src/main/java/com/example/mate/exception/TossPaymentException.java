package com.example.mate.exception;

import com.example.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class TossPaymentException extends BusinessException {
    private final String tossErrorCode;

    public TossPaymentException(String message, HttpStatusCode statusCode) {
        this("TOSS_PAYMENT_ERROR", message, statusCode, null);
    }

    public TossPaymentException(String code, String message, HttpStatusCode statusCode) {
        this(code, message, statusCode, null);
    }

    public TossPaymentException(String message, HttpStatusCode statusCode, String tossErrorCode) {
        this(tossErrorCode != null ? tossErrorCode : "TOSS_PAYMENT_ERROR", message, statusCode, tossErrorCode);
    }

    public TossPaymentException(String code, String message, HttpStatusCode statusCode, String tossErrorCode) {
        super(resolveStatus(statusCode), code, message);
        this.tossErrorCode = tossErrorCode;
    }

    public HttpStatusCode getStatusCode() {
        return getStatus();
    }

    public String getTossErrorCode() {
        return tossErrorCode;
    }

    private static HttpStatus resolveStatus(HttpStatusCode statusCode) {
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
