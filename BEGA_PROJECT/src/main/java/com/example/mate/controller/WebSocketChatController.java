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
     * WebSocket에서는 @AuthenticationPrincipal이 작동하지 않으므로
     * 메시지에 senderId를 포함해서 전송해야 함
     */
    @MessageMapping("/chat/{partyId}")
    @SendTo("/topic/party/{partyId}")
    public ChatMessageDTO.Response sendMessage(
            @DestinationVariable Long partyId,
            ChatMessageDTO.Request request,
            SimpMessageHeaderAccessor headerAccessor) {

        // 헤더에서 사용자 정보 가져오기 (선택사항)
        // Principal principal = headerAccessor.getUser();

        // DB에 메시지 저장
        ChatMessageDTO.Response savedMessage = chatMessageService.sendMessage(request);

        // 저장된 메시지를 구독자들에게 브로드캐스트
        return savedMessage;
    }
}