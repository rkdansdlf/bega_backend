package com.example.mate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ChatReadDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "MateChatReadResponse")
    public static class ReadTimestampResponse {
        private boolean success;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "MateChatUnreadCountResponse")
    public static class UnreadCountResponse {
        private boolean success;
        private long data;
    }
}
