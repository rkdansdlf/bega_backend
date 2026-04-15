package com.example.media.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.exception.MediaQuotaExceededException;
import com.example.media.repository.MediaAssetRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaQuotaService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MediaAssetRepository mediaAssetRepository;
    private final StorageConfig storageConfig;

    public void assertWithinQuota(Long userId, MediaDomain domain, long requestedBytes) {
        LocalDateTime startOfDay = LocalDate.now(SEOUL).atStartOfDay();
        long usedBytes = mediaAssetRepository.sumDailyUsageBytes(userId, startOfDay, MediaAssetStatus.DELETED);
        if (usedBytes + requestedBytes > storageConfig.getMediaDailyUploadBytesLimit()) {
            throw new MediaQuotaExceededException("일일 이미지 업로드 용량 한도를 초과했습니다.");
        }

        long usedCount = mediaAssetRepository.countByOwnerUserIdAndDomainAndCreatedAtGreaterThanEqualAndStatusNot(
                userId,
                domain,
                startOfDay,
                MediaAssetStatus.DELETED);
        if (usedCount >= resolveDailyCountLimit(domain)) {
            throw new MediaQuotaExceededException("일일 이미지 업로드 횟수 한도를 초과했습니다.");
        }
    }

    private int resolveDailyCountLimit(MediaDomain domain) {
        return switch (domain) {
            case PROFILE -> storageConfig.getMediaProfileDailyCountLimit();
            case DIARY -> storageConfig.getMediaDiaryDailyCountLimit();
            case CHEER -> storageConfig.getMediaCheerDailyCountLimit();
            case CHAT -> storageConfig.getMediaChatDailyCountLimit();
        };
    }
}
