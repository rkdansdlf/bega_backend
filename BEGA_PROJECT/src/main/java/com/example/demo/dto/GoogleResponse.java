package com.example.demo.dto;

import java.util.Map;
import java.util.Optional; // Optional 임포트 추가

public class GoogleResponse implements OAuth2Response {

    private final Map<String, Object> attribute;

    public GoogleResponse(Map<String, Object> attribute) {
        // 🚨 생성자에서 null 방어
        if (attribute == null) {
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
        // 🚨 Null 체크 로직 적용 (Optional을 사용해 안전하게 처리)
        return Optional.ofNullable(attribute.get("sub"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Google 'sub' attribute is missing."));
    }

    @Override
    public String getEmail() {
        // 🚨 Null 체크 로직 적용
        return Optional.ofNullable(attribute.get("email"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Google 'email' attribute is missing."));
    }

    @Override
    public String getName() {
        // 🚨 Null 체크 로직 적용
        return Optional.ofNullable(attribute.get("name"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Google 'name' attribute is missing."));
    }
}