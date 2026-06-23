package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StoredObjectMetadata;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.common.image.ImageUtil;
import com.example.media.dto.InitMediaUploadRequest;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaDomain;
import com.example.profile.storage.validator.ProfileImageValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
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
    @DisplayName("SVG/HTML/JSP 확장자는 선언 단계에서 거부한다")
    void validateInitRequest_rejectsScriptLikeExtensions() {
        for (String fileName : java.util.List.of("payload.svg", "payload.html", "payload.jsp")) {
            InitMediaUploadRequest request = new InitMediaUploadRequest(
                    MediaDomain.DIARY,
                    fileName,
                    "image/webp",
                    2048L,
                    640,
                    640);

            assertThatThrownBy(() -> validationService.validateInitRequest(request))
                    .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                            org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("INVALID_MEDIA_FILE_EXTENSION"));
        }
    }

    @Test
    @DisplayName("이미지 allow-list 밖 MIME 타입은 선언 단계에서 거부한다")
    void validateInitRequest_rejectsWrongDeclaredMimeType() {
        InitMediaUploadRequest request = new InitMediaUploadRequest(
                MediaDomain.DIARY,
                "payload.webp",
                "text/html",
                2048L,
                640,
                640);

        assertThatThrownBy(() -> validationService.validateInitRequest(request))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("INVALID_MEDIA_CONTENT_TYPE"));
    }

    @Test
    @DisplayName("용량 제한을 넘는 업로드 선언은 거부한다")
    void validateInitRequest_rejectsOversizedDeclaration() {
        when(storageConfig.getMaxImageBytes()).thenReturn(1024L);

        InitMediaUploadRequest request = new InitMediaUploadRequest(
                MediaDomain.DIARY,
                "payload.webp",
                "image/webp",
                2048L,
                640,
                640);

        assertThatThrownBy(() -> validationService.validateInitRequest(request))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("INVALID_MEDIA_FILE_SIZE"));
    }

    @Test
    @DisplayName("최종 업로드 검증에서 디코딩 실패 파일은 INVALID_MEDIA_UPLOAD로 거부한다")
    void validateFinalizedUpload_wrapsInvalidImageBytes() {
        MockMultipartFile malformed = new MockMultipartFile(
                "file",
                "payload.webp",
                "image/webp",
                "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("이미지 파일을 읽을 수 없습니다."))
                .when(imageValidator).validateFile(malformed);

        assertThatThrownBy(() -> validationService.validateFinalizedUpload(MediaDomain.DIARY, malformed))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("INVALID_MEDIA_UPLOAD"));
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
    @DisplayName("staged object metadata가 없으면 missing object로 거부한다")
    void validateStoredObjectMetadata_rejectsMissingObject() {
        MediaAsset asset = MediaAsset.builder()
                .declaredContentType("image/png")
                .declaredBytes(2048L)
                .build();

        assertThatThrownBy(() -> validationService.validateStoredObjectMetadata(asset, null))
                .isInstanceOfSatisfying(NotFoundBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("MEDIA_STAGING_OBJECT_NOT_FOUND"));
    }

    @Test
    @DisplayName("staged object가 서버 제한보다 크면 다운로드 전에 거부한다")
    void validateStoredObjectMetadata_rejectsOversizedObject() {
        when(storageConfig.getMaxImageBytes()).thenReturn(1024L);
        MediaAsset asset = MediaAsset.builder()
                .declaredContentType("image/png")
                .declaredBytes(2048L)
                .build();

        assertThatThrownBy(() -> validationService.validateStoredObjectMetadata(
                asset,
                new StoredObjectMetadata(2048L, "image/png")))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("INVALID_MEDIA_FILE_SIZE"));
    }

    @Test
    @DisplayName("staged object 크기가 선언값과 다르면 metadata mismatch로 거부한다")
    void validateStoredObjectMetadata_rejectsDeclaredSizeMismatch() {
        when(storageConfig.getMaxImageBytes()).thenReturn(5L * 1024L * 1024L);
        MediaAsset asset = MediaAsset.builder()
                .declaredContentType("image/png")
                .declaredBytes(2048L)
                .build();

        assertThatThrownBy(() -> validationService.validateStoredObjectMetadata(
                asset,
                new StoredObjectMetadata(1024L, "image/png")))
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
