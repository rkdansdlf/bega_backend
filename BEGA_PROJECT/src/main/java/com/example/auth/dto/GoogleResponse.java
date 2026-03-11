package com.example.auth.dto;

import java.util.Map;
import java.util.Locale;
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

    @Override
    public String getProfileImageUrl() {
        return Optional.ofNullable(attribute.get("picture"))
                .map(Object::toString)
                .filter(url -> !url.isBlank())
                .orElse(null);
    }

    @Override
    public boolean isEmailVerified() {
        return asBoolean(attribute.get("email_verified"));
    }

    @Override
    public boolean isAuthoritativeForAutoLink() {
        String email = Optional.ofNullable(attribute.get("email"))
                .map(Object::toString)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .orElse("");
        if (email.isBlank() || !isEmailVerified()) {
            return false;
        }

        if (email.endsWith("@gmail.com") || email.endsWith("@googlemail.com")) {
            return true;
        }

        return Optional.ofNullable(attribute.get("hd"))
                .map(Object::toString)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .isPresent();
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String textValue) {
            return Boolean.parseBoolean(textValue.trim());
        }
        return false;
    }
}
