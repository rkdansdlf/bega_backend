package com.example.dm.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.dm.entity.DmMessage;

public class DmMessageDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {

        @NotNull
        private Long roomId;

        @NotBlank
        @Size(max = 1000)
        private String content;

        @Size(max = 64)
        private String clientMessageId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long roomId;
        private Long senderId;
        private String content;
        private String clientMessageId;
        private Instant createdAt;

        public static Response from(DmMessage message) {
            return Response.builder()
                    .id(message.getId())
                    .roomId(message.getRoomId())
                    .senderId(message.getSenderId())
                    .content(message.getContent())
                    .clientMessageId(message.getClientMessageId())
                    .createdAt(message.getCreatedAt())
                    .build();
        }
    }
}
