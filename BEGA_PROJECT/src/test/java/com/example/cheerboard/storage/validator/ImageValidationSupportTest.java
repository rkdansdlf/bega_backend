package com.example.cheerboard.storage.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ImageValidationSupportTest {

    @Test
    void extractsExtensionAfterLastDot() {
        assertThat(ImageValidationSupport.getFileExtension("profile.photo.webp")).isEqualTo("webp");
    }

    @Test
    void rejectsMissingExtension() {
        assertThrows(IllegalArgumentException.class, () -> ImageValidationSupport.getFileExtension("profile"));
        assertThrows(IllegalArgumentException.class, () -> ImageValidationSupport.getFileExtension("profile."));
    }

    @Test
    void validatesAllowedMimeTypesCaseInsensitively() {
        ImageValidationSupport.validateMimeType("IMAGE/WEBP", Set.of("image/webp"));
    }
}
