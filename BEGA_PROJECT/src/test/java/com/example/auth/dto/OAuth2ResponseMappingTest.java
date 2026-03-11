package com.example.auth.dto;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2ResponseMappingTest {

    @Test
    void googleResponse_mapsPictureToProfileImageUrl() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("picture", "https://lh3.googleusercontent.com/avatar.png");

        OAuth2Response response = new GoogleResponse(attributes);

        assertThat(response.getProfileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/avatar.png");
    }

    @Test
    void googleResponse_authoritativeWhenVerifiedGmail() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", "user@gmail.com");
        attributes.put("email_verified", true);

        OAuth2Response response = new GoogleResponse(attributes);

        assertThat(response.isEmailVerified()).isTrue();
        assertThat(response.isAuthoritativeForAutoLink()).isTrue();
    }

    @Test
    void googleResponse_authoritativeWhenHostedDomainVerified() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", "user@company.com");
        attributes.put("email_verified", "true");
        attributes.put("hd", "company.com");

        OAuth2Response response = new GoogleResponse(attributes);

        assertThat(response.isAuthoritativeForAutoLink()).isTrue();
    }

    @Test
    void googleResponse_notAuthoritativeWithoutVerifiedEmail() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", "user@gmail.com");
        attributes.put("email_verified", false);

        OAuth2Response response = new GoogleResponse(attributes);

        assertThat(response.isEmailVerified()).isFalse();
        assertThat(response.isAuthoritativeForAutoLink()).isFalse();
    }

    @Test
    void kakaoResponse_prefersProfileImageUrl() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "kakaoUser");
        profile.put("profile_image_url", "https://profile.image/kakao.png");
        profile.put("thumbnail_image_url", "https://profile.image/kakao-thumb.png");

        Map<String, Object> kakaoAccount = new HashMap<>();
        kakaoAccount.put("profile", profile);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "123456789");
        attributes.put("kakao_account", kakaoAccount);

        OAuth2Response response = new KaKaoResponse(attributes);

        assertThat(response.getProfileImageUrl()).isEqualTo("https://profile.image/kakao.png");
    }

    @Test
    void naverResponse_readsProfileImageFromResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("profile_image", "https://profile.image/naver.png");

        Map<String, Object> root = new HashMap<>();
        root.put("response", response);

        OAuth2Response oauthResponse = new NaverResponse(root);

        assertThat(oauthResponse.getProfileImageUrl()).isEqualTo("https://profile.image/naver.png");
    }

    @Test
    void kakaoResponse_usesFallbackThumbnailWhenProfileImageMissing() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "kakaoUser");
        profile.put("thumbnail_image_url", "https://profile.image/kakao-thumb.png");

        Map<String, Object> kakaoAccount = new HashMap<>();
        kakaoAccount.put("profile", profile);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "123456789");
        attributes.put("kakao_account", kakaoAccount);

        OAuth2Response response = new KaKaoResponse(attributes);

        assertThat(response.getProfileImageUrl()).isEqualTo("https://profile.image/kakao-thumb.png");
    }

    @Test
    void kakaoResponse_treatsVerifiedEmailAsAuthoritative() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "kakaoUser");

        Map<String, Object> kakaoAccount = new HashMap<>();
        kakaoAccount.put("profile", profile);
        kakaoAccount.put("email", "user@example.com");
        kakaoAccount.put("is_email_valid", true);
        kakaoAccount.put("is_email_verified", true);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "123456789");
        attributes.put("kakao_account", kakaoAccount);

        OAuth2Response response = new KaKaoResponse(attributes);

        assertThat(response.isEmailVerified()).isTrue();
        assertThat(response.isAuthoritativeForAutoLink()).isTrue();
    }

    @Test
    void naverResponse_profileImage_blankIsNull() {
        Map<String, Object> response = Map.of("profile_image", "   ");

        Map<String, Object> root = Map.of("response", response);

        OAuth2Response oauthResponse = new NaverResponse(new HashMap<>(root));

        assertThat(oauthResponse.getProfileImageUrl()).isNull();
    }

    @Test
    void kakaoResponse_handlesMalformedNestedPayloadSafely() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("kakao_account", "not-a-map");

        OAuth2Response response = new KaKaoResponse(attributes);

        assertThatThrownBy(response::getEmail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_ACCOUNT_INFO_MISSING");
    }

    @Test
    void naverResponse_handlesMalformedNestedPayloadSafely() {
        Map<String, Object> root = new HashMap<>();
        root.put("response", "not-a-map");

        OAuth2Response response = new NaverResponse(root);

        assertThat(response.getProviderId()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getName()).isNull();
        assertThat(response.getProfileImageUrl()).isNull();
    }

    @Test
    void naverResponse_isNotAuthoritativeForAutoLink() {
        Map<String, Object> response = new HashMap<>();
        response.put("id", "naver-user");
        response.put("email", "user@naver.com");

        OAuth2Response oauthResponse = new NaverResponse(Map.of("response", response));

        assertThat(oauthResponse.isEmailVerified()).isFalse();
        assertThat(oauthResponse.isAuthoritativeForAutoLink()).isFalse();
    }
}
