package com.example.mate.dto;

import com.example.mate.entity.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

public class ChatMessageDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotNull(message = "파티 ID는 필수입니다.")
        private Long partyId;

        @Size(max = 1000, message = "메시지는 1000자 이하여야 합니다.")
        private String message;

        @Size(max = 2048, message = "이미지 URL은 2048자 이하여야 합니다.")
        private String imageUrl;

        @NotBlank(message = "clientMessageId는 필수입니다.")
        @Size(max = 64, message = "clientMessageId는 64자 이하여야 합니다.")
        private String clientMessageId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long partyId;
        private Long senderId;
        private String senderName;
        private String message;
        private String imageUrl;
        private String clientMessageId;
        private Instant createdAt;

        public static Response from(ChatMessage chatMessage) {
            return Response.builder()
                    .id(chatMessage.getId())
                    .partyId(chatMessage.getPartyId())
                    .senderId(chatMessage.getSenderId())
                    .senderName(chatMessage.getSenderName())
                    .message(chatMessage.getMessage())
                    .imageUrl(chatMessage.getImageUrl())
                    .clientMessageId(chatMessage.getClientMessageId())
                    .createdAt(chatMessage.getCreatedAt())
                    .build();
        }
    }
}
