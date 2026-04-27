package com.example.media.dto;

import com.example.media.entity.MediaDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InitMediaUploadRequest(
        @NotNull(message = "domain은 필수입니다.")
        MediaDomain domain,
        @NotBlank(message = "fileName은 필수입니다.")
        String fileName,
        @NotBlank(message = "contentType은 필수입니다.")
        String contentType,
        @NotNull(message = "contentLength는 필수입니다.")
        @Positive(message = "contentLength는 0보다 커야 합니다.")
        Long contentLength,
        @NotNull(message = "width는 필수입니다.")
        @Positive(message = "width는 0보다 커야 합니다.")
        Integer width,
        @NotNull(message = "height는 필수입니다.")
        @Positive(message = "height는 0보다 커야 합니다.")
        Integer height) {
}
