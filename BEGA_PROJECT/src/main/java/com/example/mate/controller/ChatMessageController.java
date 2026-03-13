package com.example.mate.controller;

import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    // 메시지 전송
    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(
            @RequestBody ChatMessageDTO.Request request,
            java.security.Principal principal) {
        ChatMessageDTO.Response response = chatMessageService.sendMessage(request, principal);
        messagingTemplate.convertAndSend("/topic/party/" + response.getPartyId(), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파티별 채팅 메시지 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<?> getMessagesByPartyId(
            @PathVariable Long partyId,
            java.security.Principal principal) {
        List<ChatMessageDTO.Response> messages = chatMessageService.getMessagesByPartyId(partyId, principal);
        return ResponseEntity.ok(messages);
    }

    // 파티별 최근 메시지 조회
    @GetMapping("/party/{partyId}/latest")
    public ResponseEntity<?> getLatestMessage(
            @PathVariable Long partyId,
            java.security.Principal principal) {
        ChatMessageDTO.Response message = chatMessageService.getLatestMessage(partyId, principal);
        if (message != null) {
            return ResponseEntity.ok(message);
        }
        return ResponseEntity.noContent().build();
    }
}
