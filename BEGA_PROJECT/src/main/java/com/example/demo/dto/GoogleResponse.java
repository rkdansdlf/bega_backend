package com.example.demo.dto;

import java.util.Map;
import java.util.Optional; 

/**
 * Google OAuth2 응답을 처리하는 DTO입니다.
 * Google은 사용자 정보를 최상위 Map에 직접 제공하며, 
 * 필수 필드 누락 시 명확한 예외를 발생시키도록 Optional을 사용합니다.
 */
public class GoogleResponse implements OAuth2Response {

    private final Map<String, Object> attribute;

    public GoogleResponse(Map<String, Object> attribute) {
        // 생성자에서 null 방어
        if (attribute == null) {
            // 이 예외는 CustomOAuth2UserService의 기본 로직이 실패했을 때 발생할 수 있습니다.
            throw new IllegalArgumentException("Google attributes cannot be null.");
        }
        this.attribute = attribute;
    }

    @Override
    public String getProvider() {
        return "google";
    }

    @Override
    public String getProviderId() {
        // 🚀 필수: 'sub'이 없으면 인증 불가로 판단하고 예외 발생
        return Optional.ofNullable(attribute.get("sub"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Google 'sub' (ProviderId) attribute is missing. This is required for identification."));
    }

    @Override
    public String getEmail() {
        // 🚀 필수: 'email'이 없으면 로그인 식별 불가로 판단하고 예외 발생
        return Optional.ofNullable(attribute.get("email"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Google 'email' attribute is missing. This is required for login/signup."));
    }

    @Override
    public String getName() {
        // ✅ 개선: 'name'은 선택 사항으로 처리하여, 누락되어도 로그인 흐름을 방해하지 않도록 합니다.
        // 누락 시 CustomOAuth2UserService에서 '소셜 사용자' 등으로 대체할 수 있도록 null을 반환합니다.
        return Optional.ofNullable(attribute.get("name"))
                .map(Object::toString)
                .orElse(null); // 이름이 없으면 null 반환
    }
}
