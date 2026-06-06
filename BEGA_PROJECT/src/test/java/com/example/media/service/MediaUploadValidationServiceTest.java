package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.image.ImageUtil;
import com.example.media.dto.InitMediaUploadRequest;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaDomain;
import com.example.profile.storage.validator.ProfileImageValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaUploadValidationServiceTest {

    @Mock
    private StorageConfig storageConfig;

    @Mock
    private ImageUtil imageUtil;

    @Mock
    private ImageValidator imageValidator;

    @Mock
    private ProfileImageValidator profileImageValidator;

    @InjectMocks
    private MediaUploadValidationService validationService;

    @Test
    @DisplayName("WebP 업로드 선언값이 크기와 픽셀 제한 안에 있으면 허용한다")
    void validateInitRequest_acceptsWebpWithinLimits() {
        when(storageConfig.getMaxImageBytes()).thenReturn(5L * 1024L * 1024L);
        when(storageConfig.getMaxImageLongSidePixels()).thenReturn(4096);
        when(storageConfig.getMaxImageTotalPixels()).thenReturn(16_000_000L);

        InitMediaUploadRequest request = new InitMediaUploadRequest(
                MediaDomain.CHEER,
                "post-image.webp",
                " image/webp ",
                2048L,
                1600,
                900);

        assertDoesNotThrow(() -> validationService.validateInitRequest(request));
    }

    @Test
    @DisplayName("프로필 업로드는 GIF 선언을 거부한다")
    void validateInitRequest_rejectsProfileGif() {
        InitMediaUploadRequest request = new InitMediaUploadRequest(
                MediaDomain.PROFILE,
                "avatar.gif",
                "image/gif",
                2048L,
                640,
                640);

        assertThatThrownBy(() -> validationService.validateInitRequest(request))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("INVALID_MEDIA_CONTENT_TYPE"));
    }

    @Test
    @DisplayName("실제 업로드 MIME 타입이 선언값과 다르면 거부한다")
    void validateDeclaredMatchesActual_rejectsContentTypeMismatch() {
        MediaAsset asset = MediaAsset.builder()
                .declaredContentType("image/webp")
                .declaredBytes(2048L)
                .declaredWidth(640)
                .declaredHeight(640)
                .build();

        assertThatThrownBy(() -> validationService.validateDeclaredMatchesActual(
                asset,
                new ImageUtil.ImageDimension(640, 640),
                2048L,
                "image/png"))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("MEDIA_UPLOAD_METADATA_MISMATCH"));
    }

    @Test
    @DisplayName("이미지 디코딩 실패는 INVALID_MEDIA_IMAGE로 래핑한다")
    void getActualDimension_wrapsDecodeFailure() {
        byte[] bytes = new byte[] {1, 2, 3};
        when(imageUtil.getImageDimension(bytes)).thenThrow(new IllegalArgumentException("이미지 데이터를 읽을 수 없습니다."));

        assertThatThrownBy(() -> validationService.getActualDimension(bytes))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("INVALID_MEDIA_IMAGE"));
    }
}
