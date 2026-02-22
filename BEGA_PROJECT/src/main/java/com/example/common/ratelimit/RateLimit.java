package com.example.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 요청 제한을 위한 어노테이션
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 허용되는 최대 요청 수
     */
    int limit() default 10;

    /**
     * 요청 수를 계산할 시간 창 (초)
     */
    int window() default 60;

    /**
     * 제한 구분 키 (기본값은 빈 문자열로, 엔드포인트와 IP/ID로 자동 생성)
     */
    String key() default "";
}
