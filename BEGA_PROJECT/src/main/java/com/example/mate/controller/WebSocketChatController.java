package com.example.mate.controller;

import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

@Controller
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WebSocketChatController {

    private final ChatMessageService chatMessageService;

    /**
     * 클라이언트가 /app/chat/{partyId} 로 메시지를 보내면
     * 서버가 처리 후 /topic/party/{partyId} 로 브로드캐스트
     * 
     * STOMP 세션의 Principal을 사용하여 스푸핑 방지
     */
    @MessageMapping("/chat/{partyId}")
    @SendTo("/topic/party/{partyId}")
    public ChatMessageDTO.Response sendMessage(
            @DestinationVariable Long partyId,
            ChatMessageDTO.Request request,
            java.security.Principal principal) {

        // DB에 메시지 저장 (Principal 기반 인가 확인 포함)
        ChatMessageDTO.Response savedMessage = chatMessageService.sendMessage(request, principal);

        // 저장된 메시지를 구독자들에게 브로드캐스트
        return savedMessage;
    }
}