package com.example.auth.dto;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ResponseMappingTest {

    @Test
    void googleResponse_mapsPictureToProfileImageUrl() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("picture", "https://lh3.googleusercontent.com/avatar.png");

        OAuth2Response response = new GoogleResponse(attributes);

        assertThat(response.getProfileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/avatar.png");
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
    void naverResponse_profileImage_blankIsNull() {
        Map<String, Object> response = Map.of("profile_image", "   ");

        Map<String, Object> root = Map.of("response", response);

        OAuth2Response oauthResponse = new NaverResponse(new HashMap<>(root));

        assertThat(oauthResponse.getProfileImageUrl()).isNull();
    }
}
