package com.example.mate.service;

import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.entity.ChatMessage;
import com.example.mate.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    // 메시지 전송
    @Transactional
    @SuppressWarnings("null")
    public ChatMessageDTO.Response sendMessage(ChatMessageDTO.Request request) {
        ChatMessage chatMessage = ChatMessage.builder()
                .partyId(request.getPartyId())
                .senderId(request.getSenderId())
                .senderName(request.getSenderName())
                .message(request.getMessage())
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        return ChatMessageDTO.Response.from(savedMessage);
    }

    // 파티별 채팅 메시지 조회
    @Transactional(readOnly = true)
    public List<ChatMessageDTO.Response> getMessagesByPartyId(Long partyId) {
        return chatMessageRepository.findByPartyIdOrderByCreatedAtAsc(partyId).stream()
                .map(ChatMessageDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 파티별 최근 메시지 조회
    @Transactional(readOnly = true)
    public ChatMessageDTO.Response getLatestMessage(Long partyId) {
        ChatMessage message = chatMessageRepository.findTopByPartyIdOrderByCreatedAtDesc(partyId);
        return message != null ? ChatMessageDTO.Response.from(message) : null;
    }
}