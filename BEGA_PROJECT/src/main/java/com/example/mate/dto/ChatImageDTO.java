package com.example.mate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ChatImageDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "MateChatImageUploadResponse")
    public static class UploadResponse {
        private String path;
        private String url;
    }
}
