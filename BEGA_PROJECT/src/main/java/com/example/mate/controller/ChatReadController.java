package com.example.mate.controller;

import com.example.common.web.AuthenticatedUserIds;
import com.example.mate.dto.ChatReadDTO;
import com.example.mate.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatReadController {

    private final ChatMessageService chatMessageService;

    // 특정 파티 채팅방 읽음 처리
    @PostMapping("/party/{partyId}/read")
    public ResponseEntity<ChatReadDTO.ReadTimestampResponse> updateReadTimestamp(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        chatMessageService.updateChatReadTimestamp(partyId, AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(ChatReadDTO.ReadTimestampResponse.builder()
                .success(true)
                .message("채팅 읽음 처리 완료")
                .build());
    }

    // 전체 안 읽은 메시지 수 조회
    @GetMapping("/my/unread-counts")
    public ResponseEntity<ChatReadDTO.UnreadCountResponse> getTotalUnreadCount(
            @AuthenticationPrincipal Long userId) {
        long count = chatMessageService.getTotalUnreadCount(AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(ChatReadDTO.UnreadCountResponse.builder()
                .success(true)
                .data(count)
                .build());
    }
}
