package com.example.auth.service;

import static org.mockito.Mockito.inOrder;

import com.example.ai.chat.repository.AiChatFavoriteRepository;
import com.example.ai.chat.repository.AiChatMessageRepository;
import com.example.ai.chat.repository.AiChatSessionRepository;
import com.example.dm.repository.DmMessageRepository;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.service.ChatImageService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionCommunicationServiceTest {

    @Mock
    private AiChatFavoriteRepository aiChatFavoriteRepository;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;
    @Mock
    private AiChatSessionRepository aiChatSessionRepository;
    @Mock
    private DmMessageRepository dmMessageRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatImageService chatImageService;

    @Test
    void deletesPrivateAiHistoryAndRedactsOnlyDepartingAuthorsMessages() {
        AccountDeletionCommunicationService service = new AccountDeletionCommunicationService(
                aiChatFavoriteRepository,
                aiChatMessageRepository,
                aiChatSessionRepository,
                dmMessageRepository,
                chatMessageRepository,
                chatImageService);

        org.mockito.Mockito.when(chatMessageRepository.findImageUrlsBySenderId(44L))
                .thenReturn(List.of("chat/44/legacy.webp"));

        service.anonymize(44L);

        InOrder cleanupOrder = inOrder(
                aiChatFavoriteRepository,
                aiChatMessageRepository,
                aiChatSessionRepository,
                dmMessageRepository,
                chatMessageRepository,
                chatImageService);
        cleanupOrder.verify(aiChatFavoriteRepository).deleteByUserId(44L);
        cleanupOrder.verify(aiChatFavoriteRepository).deleteByMessage_Session_UserId(44L);
        cleanupOrder.verify(aiChatMessageRepository).deleteBySession_UserId(44L);
        cleanupOrder.verify(aiChatSessionRepository).deleteByUserId(44L);
        cleanupOrder.verify(chatMessageRepository).findImageUrlsBySenderId(44L);
        cleanupOrder.verify(chatImageService).deleteLegacyChatImagesForUser(44L, List.of("chat/44/legacy.webp"));
        cleanupOrder.verify(dmMessageRepository).anonymizeBySenderId(44L, "메시지가 비식별 처리되었습니다.");
        cleanupOrder.verify(chatMessageRepository).anonymizeBySenderId(
                44L,
                "탈퇴한 사용자",
                "메시지가 비식별 처리되었습니다.");
    }
}
