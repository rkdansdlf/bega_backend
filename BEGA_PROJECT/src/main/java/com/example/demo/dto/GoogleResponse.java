package com.example.demo.dto;

import java.util.Map;
import java.util.Optional; 

public class GoogleResponse implements OAuth2Response {

    private final Map<String, Object> attribute;

    public GoogleResponse(Map<String, Object> attribute) {
        // 생성자에서 null 방어
        if (attribute == null) {
            // CustomOAuth2UserService의 기본 로직이 실패했을 경우
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
        return Optional.ofNullable(attribute.get("sub"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Google 'sub' (ProviderId) attribute is missing. This is required for identification."));
    }

    @Override
    public String getEmail() {
        return Optional.ofNullable(attribute.get("email"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Google 'email' attribute is missing. This is required for login/signup."));
    }

    @Override
    public String getName() {
        return Optional.ofNullable(attribute.get("name"))
                .map(Object::toString)
                .orElse(null); // 이름이 없으면 null 반환
    }
}
