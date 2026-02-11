package com.example.mate.controller;

import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    // 메시지 전송
    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(
            @RequestBody ChatMessageDTO.Request request,
            java.security.Principal principal) {
        try {
            ChatMessageDTO.Response response = chatMessageService.sendMessage(request, principal);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 파티별 채팅 메시지 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<ChatMessageDTO.Response>> getMessagesByPartyId(@PathVariable Long partyId) {
        List<ChatMessageDTO.Response> messages = chatMessageService.getMessagesByPartyId(partyId);
        return ResponseEntity.ok(messages);
    }

    // 파티별 최근 메시지 조회
    @GetMapping("/party/{partyId}/latest")
    public ResponseEntity<ChatMessageDTO.Response> getLatestMessage(@PathVariable Long partyId) {
        ChatMessageDTO.Response message = chatMessageService.getLatestMessage(partyId);
        if (message != null) {
            return ResponseEntity.ok(message);
        }
        return ResponseEntity.noContent().build();
    }
}