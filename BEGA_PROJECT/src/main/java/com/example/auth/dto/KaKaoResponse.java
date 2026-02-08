package com.example.auth.dto;

import java.util.Map;

public class KaKaoResponse implements OAuth2Response {

    private final Map<String, Object> attribute;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    @SuppressWarnings("unchecked")
    public KaKaoResponse(Map<String, Object> attribute) {
        this.attribute = attribute;
        // kakao_account 가져오기
        this.kakaoAccount = (Map<String, Object>) attribute.get("kakao_account");
        // kakaoAccount에서 profile 가져오기
        this.profile = (this.kakaoAccount != null) ? (Map<String, Object>) this.kakaoAccount.get("profile") : null;
    }

    @Override
    public String getProvider() {
        return "kakao";
    }

    @Override
    public String getProviderId() {
        Object id = attribute.get("id");
        return id != null ? id.toString() : null;
    }

    @Override
    public String getEmail() {
        if (kakaoAccount == null) {
            throw new IllegalStateException(
                "KAKAO_ACCOUNT_INFO_MISSING:카카오 계정 정보를 가져올 수 없습니다. 다시 시도해주세요."
            );
        }

        // 이메일 필수 동의 확인
        Boolean needsAgreement = (Boolean) kakaoAccount.get("email_needs_agreement");

        // 사용자가 이메일 제공에 동의하지 않은 경우
        if (Boolean.TRUE.equals(needsAgreement)) {
            throw new IllegalStateException(
                "KAKAO_EMAIL_CONSENT_REQUIRED:카카오 로그인 시 이메일 제공에 동의해주세요. " +
                "카카오계정 설정 > 연결된 서비스 관리 > KBO Platform에서 이메일 제공 동의를 활성화해주세요."
            );
        }

        // 이메일 유효성 확인
        Boolean emailValid = (Boolean) kakaoAccount.get("is_email_valid");
        Boolean emailVerified = (Boolean) kakaoAccount.get("is_email_verified");

        if (Boolean.FALSE.equals(emailValid) || Boolean.FALSE.equals(emailVerified)) {
            throw new IllegalStateException(
                "KAKAO_EMAIL_UNVERIFIED:카카오 계정의 이메일이 인증되지 않았습니다. " +
                "카카오 계정 설정에서 이메일 인증을 완료해주세요."
            );
        }

        // kakaoAccount에서 이메일 값 가져오기
        Object email = kakaoAccount.get("email");
        if (email == null || email.toString().isBlank()) {
            throw new IllegalStateException(
                "KAKAO_EMAIL_NOT_SET:카카오 계정에 이메일이 등록되어 있지 않습니다."
            );
        }

        return email.toString();
    }

    @Override
    public String getName() {
        if (profile == null) {
            return "이름 없음";
        }

        // profile에서 nickname 가져오기
        Object name = profile.get("nickname");

        return name != null ? name.toString() : "이름 없음";
    }
}
