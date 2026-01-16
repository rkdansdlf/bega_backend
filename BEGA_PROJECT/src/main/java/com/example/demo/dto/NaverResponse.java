package com.example.demo.dto;

import java.util.Map;
import java.util.Optional;

public class NaverResponse implements OAuth2Response {

    private final Map<String, Object> attribute;

    public NaverResponse(Map<String, Object> attribute) {
        // Naver의 속성은 "response" 키 안에 중첩되어 있습니다.
        this.attribute = (Map<String, Object>) attribute.get("response");
    }

    @Override
    public String getProvider() {
        return "naver";
    }

    @Override
    public String getProviderId() {
        return Optional.ofNullable(attribute.get("id"))
                .map(Object::toString)
                .orElse(null);
    }

    @Override
    public String getEmail() {
        return Optional.ofNullable(attribute.get("email"))
                .map(Object::toString)
                .orElse(null);
    }

    @Override
    public String getName() {
        return Optional.ofNullable(attribute.get("name"))
                .map(Object::toString)
                .orElse(null);
    }
}
