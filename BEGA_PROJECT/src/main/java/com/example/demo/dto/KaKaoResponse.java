package com.example.demo.dto;

import java.util.Map;

/**
 * Kakao OAuth2 응답을 처리하는 DTO입니다.
 * Kakao는 사용자 ID를 최상위 'id'에, 이메일과 닉네임은 'kakao_account' 내부에 중첩하여 제공합니다.
 */
public class KaKaoResponse implements OAuth2Response{

    private final Map<String, Object> attribute;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    public KaKaoResponse(Map<String, Object> attribute) {
        this.attribute = attribute;
        // 카카오 응답에서 kakao_account와 profile을 미리 추출합니다.
        this.kakaoAccount = (Map<String, Object>) attribute.get("kakao_account");
        // profile 정보는 kakaoAccount 내부에 있습니다.
        this.profile = (this.kakaoAccount != null) ? (Map<String, Object>) this.kakaoAccount.get("profile") : null;
    }

    @Override
    public String getProvider() {
        return "kakao";
    }

    @Override
    public String getProviderId() {
        // ID는 최상위 속성입니다.
        Object id = attribute.get("id");
        return id != null ? id.toString() : null;
    }

    @Override
    public String getEmail() {
        if (kakaoAccount == null) {
            return null;
        }

        // 🚨 카카오 핵심 로직: 'email_needs_agreement' 필드를 통해 사용자가 이메일 제공에 동의했는지 확인합니다.
        // 이 필드가 true이면 이메일을 사용할 수 없습니다.
        Boolean needsAgreement = (Boolean) kakaoAccount.get("email_needs_agreement");
        
        // needsAgreement가 true이면 이메일 동의가 필요한 상태이므로 null을 반환합니다.
        if (Boolean.TRUE.equals(needsAgreement)) {
            return null; 
        }

        // 2. 이메일 필드에서 값을 가져옵니다.
        Object email = kakaoAccount.get("email");
        return email != null ? email.toString() : null;
    }

    @Override
    public String getName() {
        if (profile == null) {
            return "이름 없음";
        }
        
        // profile 맵에서 nickname을 추출합니다.
        Object name = profile.get("nickname");
        
        return name != null ? name.toString() : "이름 없음";
    }
}
