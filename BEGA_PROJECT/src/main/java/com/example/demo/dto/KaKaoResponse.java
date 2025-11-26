package com.example.demo.dto;

import java.util.Map;

public class KaKaoResponse implements OAuth2Response{

    private final Map<String, Object> attribute;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

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
            return null;
        }

        // 이메일 필수 동의
        Boolean needsAgreement = (Boolean) kakaoAccount.get("email_needs_agreement");
        
        // 위 변수가 true면 null값 반환 (이메일 동의 받아야함)
        if (Boolean.TRUE.equals(needsAgreement)) {
            return null; 
        }

        // kakaoAccount에서 이메일 값 가져오기
        Object email = kakaoAccount.get("email");
        return email != null ? email.toString() : null;
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
