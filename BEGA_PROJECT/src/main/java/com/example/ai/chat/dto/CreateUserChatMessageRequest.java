package com.example.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserChatMessageRequest(
        @NotBlank(message = "content는 필수입니다.")
        @Size(max = 4000, message = "content는 최대 4000자까지 허용됩니다.")
        String content) {
}
