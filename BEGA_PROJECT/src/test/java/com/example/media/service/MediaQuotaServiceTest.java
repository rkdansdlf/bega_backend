package com.example.media.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.exception.MediaQuotaExceededException;
import com.example.media.repository.MediaAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaQuotaServiceTest {

    @InjectMocks
    private MediaQuotaService mediaQuotaService;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private StorageConfig storageConfig;

    @Test
    @DisplayName("일일 업로드 바이트 한도를 넘으면 quota 예외를 던진다")
    void assertWithinQuota_throwsWhenDailyBytesExceeded() {
        when(storageConfig.getMediaDailyUploadBytesLimit()).thenReturn(100L);
        when(mediaAssetRepository.sumDailyUsageBytes(eq(9L), any(), eq(MediaAssetStatus.DELETED))).thenReturn(90L);

        MediaQuotaExceededException exception = assertThrows(
                MediaQuotaExceededException.class,
                () -> mediaQuotaService.assertWithinQuota(9L, MediaDomain.DIARY, 20L));

        assertEquals("MEDIA_QUOTA_EXCEEDED", exception.getCode());
    }

    @Test
    @DisplayName("도메인별 일일 업로드 횟수 한도를 넘으면 quota 예외를 던진다")
    void assertWithinQuota_throwsWhenDailyCountExceeded() {
        when(storageConfig.getMediaDailyUploadBytesLimit()).thenReturn(1_000L);
        when(storageConfig.getMediaCheerDailyCountLimit()).thenReturn(2);
        when(mediaAssetRepository.sumDailyUsageBytes(eq(7L), any(), eq(MediaAssetStatus.DELETED))).thenReturn(100L);
        when(mediaAssetRepository.countByOwnerUserIdAndDomainAndCreatedAtGreaterThanEqualAndStatusNot(
                        eq(7L),
                        eq(MediaDomain.CHEER),
                        any(),
                        eq(MediaAssetStatus.DELETED)))
                .thenReturn(2L);

        MediaQuotaExceededException exception = assertThrows(
                MediaQuotaExceededException.class,
                () -> mediaQuotaService.assertWithinQuota(7L, MediaDomain.CHEER, 50L));

        assertEquals("MEDIA_QUOTA_EXCEEDED", exception.getCode());
    }

    @Test
    @DisplayName("바이트와 횟수 모두 여유가 있으면 quota 검사를 통과한다")
    void assertWithinQuota_passesWhenUsageIsWithinLimit() {
        when(storageConfig.getMediaDailyUploadBytesLimit()).thenReturn(1_000L);
        when(storageConfig.getMediaChatDailyCountLimit()).thenReturn(5);
        when(mediaAssetRepository.sumDailyUsageBytes(eq(5L), any(), eq(MediaAssetStatus.DELETED))).thenReturn(400L);
        when(mediaAssetRepository.countByOwnerUserIdAndDomainAndCreatedAtGreaterThanEqualAndStatusNot(
                        eq(5L),
                        eq(MediaDomain.CHAT),
                        any(),
                        eq(MediaAssetStatus.DELETED)))
                .thenReturn(3L);

        assertDoesNotThrow(() -> mediaQuotaService.assertWithinQuota(5L, MediaDomain.CHAT, 200L));
    }
}
