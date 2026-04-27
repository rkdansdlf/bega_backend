package com.example.auth.util;

/**
 * [Security Fix - High #2] 로그 파일에 이메일/토큰 등 민감정보가 평문으로 기록되지 않도록
 * 마스킹 유틸리티. CWE-532 (Insertion of Sensitive Information into Log File) 완화.
 *
 * <p>로그가 유출되더라도 원본 값을 복원하기 어려운 수준의 부분 마스킹을 수행한다.
 * 운영 디버깅에 필요한 최소한의 식별 정보는 유지한다.</p>
 */
public final class LogMaskingUtil {

    private LogMaskingUtil() {
    }

    /**
     * 이메일 주소를 마스킹한다.
     * <ul>
     *   <li>{@code abc@example.com} → {@code a**@example.com}</li>
     *   <li>{@code ab@example.com} → {@code a*@example.com}</li>
     *   <li>{@code a@example.com} → {@code *@example.com}</li>
     *   <li>{@code null} 또는 빈 문자열 → {@code "(none)"}</li>
     *   <li>{@code @}가 없는 잘못된 입력 → 앞 1자만 남기고 마스킹</li>
     * </ul>
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(none)";
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return maskShort(email);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at); // '@' 포함
        return maskShort(local) + domain;
    }

    /**
     * 토큰/크리덴셜을 마스킹한다. 앞 4자와 길이만 남긴다.
     * {@code eyJhbGciOi...} → {@code eyJh***(len=234)}
     */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "(none)";
        }
        int keep = Math.min(4, token.length());
        return token.substring(0, keep) + "***(len=" + token.length() + ")";
    }

    private static String maskShort(String value) {
        if (value.isEmpty()) {
            return "*";
        }
        if (value.length() == 1) {
            return "*";
        }
        return value.charAt(0) + "*".repeat(value.length() - 1);
    }
}
