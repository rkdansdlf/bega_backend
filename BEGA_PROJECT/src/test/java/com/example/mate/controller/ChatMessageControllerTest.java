package com.example.mate.controller;

import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.service.ChatMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageControllerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageController controller;

    private final Principal principal = () -> "42";

    @Test
    @DisplayName("메시지 전송 시 201을 반환하고 WebSocket으로 브로드캐스트한다")
    void sendMessage_returns201AndBroadcasts() {
        ChatMessageDTO.Request req = ChatMessageDTO.Request.builder().partyId(5L).message("hi").build();
        ChatMessageDTO.Response resp = ChatMessageDTO.Response.builder().id(1L).partyId(5L).message("hi").build();
        when(chatMessageService.sendMessage(req, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.sendMessage(req, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(resp);
        verify(messagingTemplate).convertAndSend("/topic/party/5", resp);
    }

    @Test
    @DisplayName("파티별 메시지 목록을 조회한다")
    void getMessagesByPartyId_returnsList() {
        ChatMessageDTO.Response resp = ChatMessageDTO.Response.builder().id(1L).build();
        when(chatMessageService.getMessagesByPartyId(5L, principal)).thenReturn(List.of(resp));

        ResponseEntity<?> result = controller.getMessagesByPartyId(5L, principal);

        assertThat((List<?>) result.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("최신 메시지가 있으면 200을 반환한다")
    void getLatestMessage_whenExists_returnsMessage() {
        ChatMessageDTO.Response resp = ChatMessageDTO.Response.builder().id(1L).build();
        when(chatMessageService.getLatestMessage(5L, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.getLatestMessage(5L, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("최신 메시지가 없으면 204를 반환한다")
    void getLatestMessage_whenNull_returns204() {
        when(chatMessageService.getLatestMessage(5L, principal)).thenReturn(null);

        ResponseEntity<?> result = controller.getLatestMessage(5L, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
