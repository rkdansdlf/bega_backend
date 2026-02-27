package com.example.mate.controller;

import com.example.mate.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatReadController {

    private final ChatMessageService chatMessageService;

    // 특정 파티 채팅방 읽음 처리
    @PostMapping("/party/{partyId}/read")
    public ResponseEntity<?> updateReadTimestamp(@PathVariable Long partyId, Principal principal) {
        chatMessageService.updateChatReadTimestamp(partyId, principal);
        return ResponseEntity.ok(Map.of("success", true, "message", "채팅 읽음 처리 완료"));
    }

    // 전체 안 읽은 메시지 수 조회
    @GetMapping("/my/unread-counts")
    public ResponseEntity<?> getTotalUnreadCount(Principal principal) {
        long count = chatMessageService.getTotalUnreadCount(principal);
        return ResponseEntity.ok(Map.of("success", true, "data", count));
    }
}
