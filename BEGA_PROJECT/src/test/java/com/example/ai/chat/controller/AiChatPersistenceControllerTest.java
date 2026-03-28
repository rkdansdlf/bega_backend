package com.example.ai.chat.controller;

import com.example.ai.chat.dto.ChatSessionSummary;
import com.example.ai.chat.service.AiChatPersistenceService;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatPersistenceControllerTest {

    @Mock
    private AiChatPersistenceService aiChatPersistenceService;

    @InjectMocks
    private AiChatPersistenceController controller;

    @Test
    void listSessions_returnsApiResponse() {
        when(aiChatPersistenceService.listSessions(1L)).thenReturn(List.of(
                new ChatSessionSummary(10L, "세션", 2, "preview", Instant.now(), Instant.now(), Instant.now())));

        ResponseEntity<ApiResponse> response = controller.listSessions(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void createSession_propagatesAuthenticationFailure() {
        when(aiChatPersistenceService.createSession(null)).thenThrow(new AuthenticationRequiredException("인증이 필요합니다."));

        assertThatThrownBy(() -> controller.createSession(null))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessageContaining("인증이 필요합니다.");
    }
}
