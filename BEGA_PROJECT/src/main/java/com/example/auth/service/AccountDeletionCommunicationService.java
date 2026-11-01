package com.example.auth.service;

import com.example.ai.chat.repository.AiChatFavoriteRepository;
import com.example.ai.chat.repository.AiChatMessageRepository;
import com.example.ai.chat.repository.AiChatSessionRepository;
import com.example.dm.repository.DmMessageRepository;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.service.ChatImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes private AI history and removes the departing account's authored message content. */
@Service
@RequiredArgsConstructor
public class AccountDeletionCommunicationService {

    private static final String REDACTED_MESSAGE = "메시지가 비식별 처리되었습니다.";
    private static final String REDACTED_SENDER = "탈퇴한 사용자";

    private final AiChatFavoriteRepository aiChatFavoriteRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final DmMessageRepository dmMessageRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatImageService chatImageService;

    @Transactional
    public void anonymize(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted account ID is required for finalization.");
        }

        aiChatFavoriteRepository.deleteByUserId(userId);
        aiChatFavoriteRepository.deleteByMessage_Session_UserId(userId);
        aiChatMessageRepository.deleteBySession_UserId(userId);
        aiChatSessionRepository.deleteByUserId(userId);
        chatImageService.deleteLegacyChatImagesForUser(
                userId,
                chatMessageRepository.findImageUrlsBySenderId(userId));
        dmMessageRepository.anonymizeBySenderId(userId, REDACTED_MESSAGE);
        chatMessageRepository.anonymizeBySenderId(userId, REDACTED_SENDER, REDACTED_MESSAGE);
    }
}
