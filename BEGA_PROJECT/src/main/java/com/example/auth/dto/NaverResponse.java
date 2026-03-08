package com.example.auth.dto;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

public class NaverResponse implements OAuth2Response {

    private final Map<String, Object> attribute;

    public NaverResponse(Map<String, Object> attribute) {
        // Naver의 속성은 "response" 키 안에 중첩되어 있습니다.
        Map<String, Object> root = attribute != null ? attribute : Map.of();
        Map<String, Object> nested = asMap(root.get("response"));
        this.attribute = nested != null ? nested : Map.of();
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

    @Override
    public String getProfileImageUrl() {
        return Optional.ofNullable(attribute.get("profile_image"))
                .map(Object::toString)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .orElse(null);
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> converted = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) {
                converted.put(key, entry.getValue());
            }
        }
        return converted;
    }
}
