package com.example.common.exception;

import java.util.Map;

/**
 * 비밀번호는 맞지만 이메일 인증을 마치지 않은 이메일 회원가입 계정의 로그인 시도.
 * 프론트는 code=EMAIL_NOT_VERIFIED 로 "인증 메일 재발송" 화면을 띄운다.
 */
public class EmailNotVerifiedException extends ForbiddenBusinessException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException(String email) {
        super(CODE,
                "이메일 인증이 필요합니다. 가입 시 받은 인증 메일의 링크를 열어주세요.",
                Map.of("email", email, "emailVerificationRequired", true));
    }
}
