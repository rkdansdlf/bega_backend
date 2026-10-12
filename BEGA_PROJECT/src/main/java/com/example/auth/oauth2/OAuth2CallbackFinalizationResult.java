package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.dto.UserDto;

public record OAuth2CallbackFinalizationResult(
        UserEntity user,
        UserDto userDto,
        String accessToken,
        int accessTokenMaxAgeSeconds,
        int refreshTokenMaxAgeSeconds,
        String stateId,
        String refreshToken,
        boolean linkFlow) {
}
