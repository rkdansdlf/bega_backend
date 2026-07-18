package com.example.mate.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.Test;

import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.service.ChatMessageService;

class WebSocketChatControllerTest {

    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final WebSocketChatController controller = new WebSocketChatController(chatMessageService);

    @Test
    void sendMessageDelegatesDurablePublishToService() {
        ChatMessageDTO.Request request = ChatMessageDTO.Request.builder().message("hello").build();
        ChatMessageDTO.Response response = ChatMessageDTO.Response.builder()
                .id(7L)
                .partyId(5L)
                .message("hello")
                .build();
        Principal principal = () -> "42";
        when(chatMessageService.sendMessage(request, principal)).thenReturn(response);

        controller.sendMessage(5L, request, principal);

        org.assertj.core.api.Assertions.assertThat(request.getPartyId()).isEqualTo(5L);
        verify(chatMessageService).sendMessage(request, principal);
    }

    @Test
    void sendMessageHasNoImplicitSpringReturnDestination() throws Exception {
        org.assertj.core.api.Assertions.assertThat(WebSocketChatController.class.getDeclaredMethod(
                "sendMessage",
                Long.class,
                ChatMessageDTO.Request.class,
                Principal.class).getReturnType()).isEqualTo(Void.TYPE);
    }
}
