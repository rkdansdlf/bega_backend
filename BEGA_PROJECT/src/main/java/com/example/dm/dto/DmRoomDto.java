package com.example.dm.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class DmRoomDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BootstrapRequest {

        @NotBlank
        private String targetHandle;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TargetUser {
        private Long id;
        private String name;
        private String handle;
        private String favoriteTeam;
        private String profileImageUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BootstrapResponse {
        private Long roomId;
        private String membershipState;
        private TargetUser targetUser;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboxItem {
        private Long roomId;
        private TargetUser targetUser;
        private LastMessagePreview lastMessage;
        private boolean hasUnread;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class LastMessagePreview {
            private String content;
            private Instant createdAt;
            private Long senderId;
        }
    }
}
