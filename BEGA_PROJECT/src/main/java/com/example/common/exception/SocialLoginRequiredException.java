package com.example.common.exception;

public class SocialLoginRequiredException extends ForbiddenBusinessException {
    public SocialLoginRequiredException() {
        super("SOCIAL_LOGIN_REQUIRED", "이 계정은 소셜 로그인 전용입니다. 소셜 로그인을 이용해 주세요.");
    }
}
