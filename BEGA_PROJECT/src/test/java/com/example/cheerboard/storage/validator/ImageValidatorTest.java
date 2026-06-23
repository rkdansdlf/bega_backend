package com.example.cheerboard.storage.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.common.image.ImageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ImageValidatorTest {

    @Mock
    private StorageConfig config;

    @Mock
    private ImageUtil imageUtil;

    @Mock
    private MultipartFile file;

    @InjectMocks
    private ImageValidator validator;

    @Test
    void validateFile_acceptsImageWithinPixelLimits() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("photo.webp");
        when(file.getContentType()).thenReturn("image/webp");
        when(file.getSize()).thenReturn(1024L);
        when(config.getMaxImageBytes()).thenReturn(5L * 1024L * 1024L);
        when(config.getMaxImageLongSidePixels()).thenReturn(4096);
        when(config.getMaxImageTotalPixels()).thenReturn(16_000_000L);
        when(imageUtil.getImageDimension(file)).thenReturn(new ImageUtil.ImageDimension(2048, 2048));

        assertDoesNotThrow(() -> validator.validateFile(file));
    }

    @Test
    void validateFile_rejectsImageWhenTotalPixelsExceedLimit() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1024L);
        when(config.getMaxImageBytes()).thenReturn(5L * 1024L * 1024L);
        when(config.getMaxImageLongSidePixels()).thenReturn(4096);
        when(config.getMaxImageTotalPixels()).thenReturn(16_000_000L);
        when(imageUtil.getImageDimension(file)).thenReturn(new ImageUtil.ImageDimension(4000, 4001));

        assertThrows(IllegalArgumentException.class, () -> validator.validateFile(file));
    }

    @Test
    void validateFile_rejectsScriptLikeExtensions() {
        for (String filename : java.util.List.of("xss.svg", "xss.html", "shell.jsp")) {
            MockMultipartFile scriptFile = new MockMultipartFile(
                    "file",
                    filename,
                    "image/svg+xml",
                    "<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThrows(IllegalArgumentException.class, () -> validator.validateFile(scriptFile));
        }
    }

    @Test
    void validateFile_rejectsWrongDeclaredMimeType() {
        MockMultipartFile htmlAsImage = new MockMultipartFile(
                "file",
                "photo.webp",
                "text/html",
                "<html></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> validator.validateFile(htmlAsImage));
    }

    @Test
    void validateFile_rejectsOversizedFilesBeforeDecoding() {
        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "photo.webp",
                "image/webp",
                new byte[2048]);
        when(config.getMaxImageBytes()).thenReturn(1024L);

        assertThrows(IllegalArgumentException.class, () -> validator.validateFile(oversized));
    }

    @Test
    void validateFile_rejectsInvalidImageBytes() {
        MockMultipartFile malformed = new MockMultipartFile(
                "file",
                "photo.webp",
                "image/webp",
                "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(config.getMaxImageBytes()).thenReturn(5L * 1024L * 1024L);
        when(imageUtil.getImageDimension(malformed)).thenThrow(new IllegalArgumentException("decode failed"));

        assertThrows(IllegalArgumentException.class, () -> validator.validateFile(malformed));
    }
}
