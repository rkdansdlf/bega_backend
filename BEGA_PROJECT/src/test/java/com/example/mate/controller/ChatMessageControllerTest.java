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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageControllerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @InjectMocks
    private ChatMessageController controller;

    @Test
    @DisplayName("메시지 전송 시 서비스 결과와 201을 반환한다")
    void sendMessage_returns201() {
        ChatMessageDTO.Request req = ChatMessageDTO.Request.builder().partyId(5L).message("hi").build();
        ChatMessageDTO.Response resp = ChatMessageDTO.Response.builder().id(1L).partyId(5L).message("hi").build();
        when(chatMessageService.sendMessage(req, 42L)).thenReturn(resp);

        ResponseEntity<?> result = controller.sendMessage(req, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(resp);
        verify(chatMessageService).sendMessage(req, 42L);
    }

    @Test
    @DisplayName("파티별 메시지 목록을 조회한다")
    void getMessagesByPartyId_returnsList() {
        ChatMessageDTO.Response resp = ChatMessageDTO.Response.builder().id(1L).build();
        when(chatMessageService.getMessagesByPartyId(5L, 42L)).thenReturn(List.of(resp));

        ResponseEntity<?> result = controller.getMessagesByPartyId(5L, 42L);

        assertThat((List<?>) result.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("파티별 메시지 조회 시 페이지 커서를 서비스에 전달한다")
    void getMessagesByPartyId_forwardsPagingArguments() {
        ChatMessageDTO.Response resp = ChatMessageDTO.Response.builder().id(1L).build();
        when(chatMessageService.getMessagesByPartyId(5L, 42L, 20, 100L)).thenReturn(List.of(resp));

        ResponseEntity<?> result = controller.getMessagesByPartyId(5L, 42L, 20, 100L);

        assertThat((List<?>) result.getBody()).hasSize(1);
        assertThat(((List<?>) result.getBody()).get(0)).isEqualTo(resp);
        verify(chatMessageService).getMessagesByPartyId(5L, 42L, 20, 100L);
    }

    @Test
    @DisplayName("채팅 페이지 파라미터에 공개 API 제약 조건을 선언한다")
    void getMessagesByPartyId_declaresPagingConstraints() throws Exception {
        Method method = ChatMessageController.class.getMethod(
                "getMessagesByPartyId", Long.class, Long.class, Integer.class, Long.class);

        assertThat(method.getParameters()[2].getAnnotation(Min.class).value()).isEqualTo(1L);
        assertThat(method.getParameters()[2].getAnnotation(Max.class).value()).isEqualTo(100L);
        assertThat(method.getParameters()[3].getAnnotation(Positive.class)).isNotNull();
    }

    @Test
    @DisplayName("최신 메시지가 있으면 200을 반환한다")
    void getLatestMessage_whenExists_returnsMessage() {
        ChatMessageDTO.Response resp = ChatMessageDTO.Response.builder().id(1L).build();
        when(chatMessageService.getLatestMessage(5L, 42L)).thenReturn(resp);

        ResponseEntity<?> result = controller.getLatestMessage(5L, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("최신 메시지가 없으면 204를 반환한다")
    void getLatestMessage_whenNull_returns204() {
        when(chatMessageService.getLatestMessage(5L, 42L)).thenReturn(null);

        ResponseEntity<?> result = controller.getLatestMessage(5L, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
