package com.example.mate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.common.image.ImageUtil;
import com.example.media.service.MediaObjectKeyGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
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

    @Mock
    private ImageValidator imageValidator;

    @Mock
    private MediaObjectKeyGuard mediaObjectKeyGuard;

    @Test
    @DisplayName("채팅 이미지 resolver는 legacy signed URL에서도 canonical key를 추출해 재서명한다")
    void resolveChatImageUrl_reSignsManagedSignedUrl() {
        when(storageConfig.getCheerBucket()).thenReturn("cheer-bucket");
        when(storageConfig.getSignedUrlTtlSeconds()).thenReturn(600);
        when(storageStrategy.getUrl("cheer-bucket", "media/chat/3/77.webp", 600))
                .thenReturn(Mono.just("https://signed.example/media/chat/3/77.webp"));

        String resolved = chatImageService.resolveChatImageUrlUnchecked("https://cdn.example.com/media/chat/3/77.webp?sig=old");

        assertThat(resolved).isEqualTo("https://signed.example/media/chat/3/77.webp");
        assertThat(chatImageService.normalizeChatStoragePath("https://cdn.example.com/media/chat/3/77.webp?sig=old"))
                .isEqualTo("media/chat/3/77.webp");
    }

    @Test
    @DisplayName("채팅 이미지 저장 검증은 다른 사용자의 legacy chat key를 거부한다")
    void normalizeChatStoragePathForUser_rejectsCrossOwnerLegacyPath() {
        org.mockito.Mockito.doThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요."))
                .when(mediaObjectKeyGuard)
                .requireChatWriteKey("chat/99/avatar.webp", 3L);

        assertThatThrownBy(() -> chatImageService.normalizeChatStoragePathForUser(3L, "chat/99/avatar.webp"))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code")
                .isEqualTo("MEDIA_ASSET_NOT_FOUND");
    }

    @Test
    @DisplayName("owner-aware 채팅 이미지 resolver는 다른 사용자의 legacy chat key를 서명하지 않는다")
    void resolveChatImageUrlForUser_doesNotSignCrossOwnerLegacyPath() {
        String resolved = chatImageService.resolveChatImageUrlForUser(3L, "chat/99/avatar.webp");

        assertThat(resolved).isNull();
        verify(storageStrategy, never()).getUrl(any(), any(), anyInt());
    }

    @Test
    @DisplayName("owner-aware 채팅 이미지 resolver는 본인의 legacy chat key를 서명한다")
    void resolveChatImageUrlForUser_signsOwnedLegacyPath() {
        when(storageConfig.getCheerBucket()).thenReturn("cheer-bucket");
        when(storageConfig.getSignedUrlTtlSeconds()).thenReturn(600);
        when(mediaObjectKeyGuard.canReadChatKey("chat/3/avatar.webp", 3L)).thenReturn(true);
        when(storageStrategy.getUrl("cheer-bucket", "chat/3/avatar.webp", 600))
                .thenReturn(Mono.just("https://signed.example/chat/3/avatar.webp"));

        String resolved = chatImageService.resolveChatImageUrlForUser(3L, "chat/3/avatar.webp");

        assertThat(resolved).isEqualTo("https://signed.example/chat/3/avatar.webp");
        verify(storageStrategy).getUrl("cheer-bucket", "chat/3/avatar.webp", 600);
    }

    @Test
    @DisplayName("owner-aware 채팅 이미지 resolver는 managed asset 검증 실패 시 서명하지 않는다")
    void resolveChatImageUrlForUser_doesNotSignManagedPathWhenLedgerRejects() {
        when(mediaObjectKeyGuard.canReadChatKey("media/chat/3/77.webp", 3L)).thenReturn(false);

        String resolved = chatImageService.resolveChatImageUrlForUser(3L, "media/chat/3/77.webp");

        assertThat(resolved).isNull();
        verify(storageStrategy, never()).getUrl(any(), any(), anyInt());
    }

    @Test
    @DisplayName("채팅 메시지 저장용 정규화는 full URL에 포함된 managed path를 거부한다")
    void normalizeChatStoragePathForUser_rejectsEmbeddedManagedFullUrl() {
        assertThatThrownBy(() -> chatImageService.normalizeChatStoragePathForUser(
                3L,
                "https://evil.example.com/media/chat/3/77.webp?sig=old"))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("MEDIA_ASSET_NOT_FOUND"));
    }

    @Test
    @DisplayName("채팅 메시지 저장용 정규화는 현재 사용자의 managed key와 legacy key를 허용한다")
    void normalizeChatStoragePathForUser_acceptsOwnedStorageKeys() {
        assertThat(chatImageService.normalizeChatStoragePathForUser(3L, "media/chat/3/77.webp"))
                .isEqualTo("media/chat/3/77.webp");
        assertThat(chatImageService.normalizeChatStoragePathForUser(3L, "chat/3/legacy.webp"))
                .isEqualTo("chat/3/legacy.webp");
    }

    @Test
    @DisplayName("채팅 이미지 업로드는 공통 화이트리스트 validator 거부 시 저장 전에 중단한다")
    void uploadChatImage_rejectsValidatorFailureBeforeStorage() throws Exception {
        MockMultipartFile svg = new MockMultipartFile(
                "file",
                "payload.svg",
                "image/svg+xml",
                "<svg><script>alert(1)</script></svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("허용되지 않는 파일 형식입니다."))
                .when(imageValidator).validateFile(svg);

        assertThatThrownBy(() -> chatImageService.uploadChatImage(10L, svg))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("허용되지 않는 파일 형식입니다.");

        verify(imageUtil, never()).process(any(), any());
        verify(storageStrategy, never()).uploadBytes(any(), any(), any(), any());
    }
}
