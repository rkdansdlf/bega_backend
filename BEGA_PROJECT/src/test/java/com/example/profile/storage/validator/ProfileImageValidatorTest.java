package com.example.profile.storage.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.common.image.ImageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ProfileImageValidatorTest {

    @Mock
    private StorageConfig config;

    @Mock
    private ImageUtil imageUtil;

    @Mock
    private MultipartFile file;

    @InjectMocks
    private ProfileImageValidator validator;

    @Test
    void validateProfileImage_acceptsImageWithSufficientResolution() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("avatar.webp");
        when(file.getContentType()).thenReturn("image/webp");
        when(file.getSize()).thenReturn(1024L);
        when(config.getMaxImageBytes()).thenReturn(1024L * 1024L);
        when(imageUtil.getImageDimension(file)).thenReturn(new ImageUtil.ImageDimension(256, 256));

        assertDoesNotThrow(() -> validator.validateProfileImage(file));
    }

    @Test
    void validateProfileImage_rejectsImageBelowMinimumShortSide() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("avatar.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1024L);
        when(config.getMaxImageBytes()).thenReturn(1024L * 1024L);
        when(imageUtil.getImageDimension(file)).thenReturn(new ImageUtil.ImageDimension(128, 512));

        assertThrows(IllegalArgumentException.class, () -> validator.validateProfileImage(file));
    }

    @Test
    void validateProfileImage_rejectsUnsupportedFileExtension() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("avatar.txt");

        assertThrows(IllegalArgumentException.class, () -> validator.validateProfileImage(file));
    }
}
