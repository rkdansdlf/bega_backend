package com.example.ai.chat.service;

import com.example.ai.chat.dto.ChatFavoriteItem;
import com.example.ai.chat.dto.CreateAssistantChatMessageRequest;
import com.example.ai.chat.dto.CreateUserChatMessageRequest;
import com.example.ai.chat.dto.StoredChatMessage;
import com.example.ai.chat.entity.AiChatFavorite;
import com.example.ai.chat.entity.AiChatMessage;
import com.example.ai.chat.entity.AiChatMessageRole;
import com.example.ai.chat.entity.AiChatMessageStatus;
import com.example.ai.chat.entity.AiChatSession;
import com.example.ai.chat.repository.AiChatFavoriteRepository;
import com.example.ai.chat.repository.AiChatMessageRepository;
import com.example.ai.chat.repository.AiChatSessionRepository;
import com.example.common.exception.ForbiddenBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatPersistenceServiceTest {

    @Mock
    private AiChatSessionRepository sessionRepository;

    @Mock
    private AiChatMessageRepository messageRepository;

    @Mock
    private AiChatFavoriteRepository favoriteRepository;

    private AiChatPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new AiChatPersistenceService(
                sessionRepository,
                messageRepository,
                favoriteRepository,
                new ObjectMapper());
    }

    @Test
    void addUserMessage_updatesFirstSessionTitleFromPrompt() {
        AiChatSession session = AiChatSession.builder()
                .id(10L)
                .userId(1L)
                .title(AiChatSession.DEFAULT_TITLE)
                .messageCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        when(sessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            message.setId(100L);
            return message;
        });

        StoredChatMessage stored = service.addUserMessage(
                1L,
                10L,
                new CreateUserChatMessageRequest("LG 불펜 최근 흐름 알려줘"));

        assertThat(stored.content()).isEqualTo("LG 불펜 최근 흐름 알려줘");
        assertThat(session.getTitle()).isEqualTo("LG 불펜 최근 흐름 알려줘");
        assertThat(session.getMessageCount()).isEqualTo(1);
    }

    @Test
    void addUserMessage_normalizesNullableLongTextFieldsForPersistence() {
        AiChatSession session = AiChatSession.builder()
                .id(10L)
                .userId(1L)
                .title(AiChatSession.DEFAULT_TITLE)
                .messageCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        when(sessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            message.setId(101L);
            return message;
        });

        service.addUserMessage(1L, 10L, new CreateUserChatMessageRequest("저장 테스트"));

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageRepository).save(captor.capture());
        AiChatMessage saved = captor.getValue();

        assertThat(saved.getMetadataJson()).isEmpty();
        assertThat(saved.getCitationsJson()).isEmpty();
        assertThat(saved.getToolCallsJson()).isEmpty();
    }

    @Test
    void addFavorite_rejectsIncompleteAssistantMessage() {
        AiChatSession session = AiChatSession.builder()
                .id(10L)
                .userId(1L)
                .title("세션")
                .messageCount(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        AiChatMessage message = AiChatMessage.builder()
                .id(200L)
                .session(session)
                .role(AiChatMessageRole.ASSISTANT)
                .status(AiChatMessageStatus.CANCELLED)
                .content("응답을 취소했습니다.")
                .build();
        when(favoriteRepository.findByUserIdAndMessage_Id(1L, 200L)).thenReturn(Optional.empty());
        when(messageRepository.findById(200L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.addFavorite(1L, 200L))
                .isInstanceOf(ForbiddenBusinessException.class)
                .hasMessageContaining("완료된 assistant 메시지");

        verify(favoriteRepository, never()).save(any(AiChatFavorite.class));
    }

    @Test
    void listMessages_marksFavorites() {
        AiChatSession session = AiChatSession.builder()
                .id(10L)
                .userId(1L)
                .title("세션")
                .messageCount(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        AiChatMessage message = AiChatMessage.builder()
                .id(301L)
                .session(session)
                .role(AiChatMessageRole.ASSISTANT)
                .status(AiChatMessageStatus.COMPLETED)
                .content("답변")
                .build();
        AiChatFavorite favorite = AiChatFavorite.builder()
                .id(401L)
                .userId(1L)
                .message(message)
                .createdAt(Instant.now())
                .build();

        when(sessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySession_IdOrderByCreatedAtAsc(10L)).thenReturn(List.of(message));
        when(favoriteRepository.findByUserIdAndMessage_IdIn(1L, List.of(301L))).thenReturn(List.of(favorite));

        List<StoredChatMessage> result = service.listMessages(1L, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).favorite()).isTrue();
    }

    @Test
    void addAssistantMessage_persistsPlannerMetadata() {
        AiChatSession session = AiChatSession.builder()
                .id(10L)
                .userId(1L)
                .title("세션")
                .messageCount(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        when(sessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            message.setId(500L);
            return message;
        });

        StoredChatMessage result = service.addAssistantMessage(
                1L,
                10L,
                new CreateAssistantChatMessageRequest(
                        "답변 본문",
                        "COMPLETED",
                        true,
                        false,
                        "team_analysis",
                        "fast_path",
                        "stop",
                        false,
                        null,
                        "fast_path",
                        true,
                        "parallel",
                        null,
                        new ObjectMapper().createObjectNode().put("first_token_ms", 123),
                        new ObjectMapper().createArrayNode(),
                        new ObjectMapper().createArrayNode()));

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageRepository).save(captor.capture());
        AiChatMessage saved = captor.getValue();

        assertThat(saved.getPlannerMode()).isEqualTo("fast_path");
        assertThat(saved.getPlannerCacheHit()).isTrue();
        assertThat(saved.getToolExecutionMode()).isEqualTo("parallel");
        assertThat(result.verified()).isTrue();
    }

    @Test
    void addFavorite_includesPreviousUserPrompt() {
        Instant now = Instant.now();
        AiChatSession session = AiChatSession.builder()
                .id(10L)
                .userId(1L)
                .title("세션")
                .messageCount(2)
                .createdAt(now)
                .updatedAt(now)
                .lastMessageAt(now)
                .build();
        AiChatMessage assistant = AiChatMessage.builder()
                .id(200L)
                .session(session)
                .role(AiChatMessageRole.ASSISTANT)
                .status(AiChatMessageStatus.COMPLETED)
                .content("assistant answer")
                .createdAt(now)
                .updatedAt(now)
                .build();
        AiChatMessage user = AiChatMessage.builder()
                .id(199L)
                .session(session)
                .role(AiChatMessageRole.USER)
                .status(AiChatMessageStatus.COMPLETED)
                .content("original question")
                .createdAt(now)
                .updatedAt(now)
                .build();
        when(favoriteRepository.findByUserIdAndMessage_Id(1L, 200L)).thenReturn(Optional.empty());
        when(messageRepository.findById(200L)).thenReturn(Optional.of(assistant));
        when(messageRepository.findTopBySession_IdAndIdLessThanAndRoleOrderByIdDesc(
                10L, 200L, AiChatMessageRole.USER)).thenReturn(Optional.of(user));
        when(favoriteRepository.save(any(AiChatFavorite.class))).thenAnswer(invocation -> {
            AiChatFavorite favorite = invocation.getArgument(0);
            favorite.setId(300L);
            favorite.setCreatedAt(now);
            return favorite;
        });

        ChatFavoriteItem item = service.addFavorite(1L, 200L);

        assertThat(item.prompt()).isEqualTo("original question");
    }
}
