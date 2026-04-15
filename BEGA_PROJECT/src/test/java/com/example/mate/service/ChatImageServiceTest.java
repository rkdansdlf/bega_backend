package com.example.mate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.common.image.ImageUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ChatImageServiceTest {

    @InjectMocks
    private ChatImageService chatImageService;

    @Mock
    private StorageStrategy storageStrategy;

    @Mock
    private StorageConfig storageConfig;

    @Mock
    private ImageUtil imageUtil;

    @Mock
    private ImageOptimizationMetricsService metricsService;

    @Test
    @DisplayName("채팅 이미지 resolver는 legacy signed URL에서도 canonical key를 추출해 재서명한다")
    void resolveChatImageUrl_reSignsManagedSignedUrl() {
        when(storageConfig.getCheerBucket()).thenReturn("cheer-bucket");
        when(storageConfig.getSignedUrlTtlSeconds()).thenReturn(600);
        when(storageStrategy.getUrl("cheer-bucket", "media/chat/3/77.webp", 600))
                .thenReturn(Mono.just("https://signed.example/media/chat/3/77.webp"));

        String resolved = chatImageService.resolveChatImageUrl("https://cdn.example.com/media/chat/3/77.webp?sig=old");

        assertThat(resolved).isEqualTo("https://signed.example/media/chat/3/77.webp");
        assertThat(chatImageService.normalizeChatStoragePath("https://cdn.example.com/media/chat/3/77.webp?sig=old"))
                .isEqualTo("media/chat/3/77.webp");
    }
}
