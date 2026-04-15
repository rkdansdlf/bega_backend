package com.example.media.service;

import com.example.common.exception.RateLimitExceededException;
import com.example.common.ratelimit.RateLimitService;
import com.example.media.entity.MediaDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaRateLimitService {

    private final RateLimitService rateLimitService;

    public void assertUploadAllowed(Long userId, MediaDomain domain) {
        String key = "media:init:" + domain.name().toLowerCase() + ":" + userId;
        int limit = switch (domain) {
            case PROFILE -> 3;
            case DIARY -> 10;
            case CHEER -> 10;
            case CHAT -> 20;
        };
        if (!rateLimitService.isAllowed(key, limit, 60)) {
            throw new RateLimitExceededException("너무 많은 이미지 업로드 요청을 보냈습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
