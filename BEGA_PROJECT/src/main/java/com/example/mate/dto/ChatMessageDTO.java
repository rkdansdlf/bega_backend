package com.example.mate.dto;

import com.example.mate.entity.ChatMessage;
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
        private Long partyId;
        private Long senderId;
        private String senderName;
        private String message;
        private String imageUrl;
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
        private Instant createdAt;

        public static Response from(ChatMessage chatMessage) {
            return Response.builder()
                    .id(chatMessage.getId())
                    .partyId(chatMessage.getPartyId())
                    .senderId(chatMessage.getSenderId())
                    .senderName(chatMessage.getSenderName())
                    .message(chatMessage.getMessage())
                    .imageUrl(chatMessage.getImageUrl())
                    .createdAt(chatMessage.getCreatedAt())
                    .build();
        }
    }
}