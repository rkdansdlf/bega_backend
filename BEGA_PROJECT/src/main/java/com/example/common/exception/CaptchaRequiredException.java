package com.example.common.exception;

public class CaptchaRequiredException extends BadRequestBusinessException {
    public CaptchaRequiredException() {
        super("CAPTCHA_REQUIRED", "추가 인증이 필요합니다.");
    }
}
