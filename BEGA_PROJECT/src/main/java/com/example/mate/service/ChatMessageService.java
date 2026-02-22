package com.example.mate.service;

import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.entity.ChatMessage;
import com.example.mate.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.security.Principal;
import java.util.stream.Collectors;
import com.example.auth.service.UserService;
import com.example.auth.repository.UserRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.exception.PartyNotFoundException;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PartyApplicationRepository applicationRepository;

    // 메시지 전송
    @Transactional
    public ChatMessageDTO.Response sendMessage(ChatMessageDTO.Request request, Principal principal) {
        Long userId = userService.getUserIdByEmail(principal.getName());

        // 파티 존재 확인 및 멤버 여부 확인
        var party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(request.getPartyId()));

        boolean isMember = party.getHostId().equals(userId) ||
                applicationRepository.findByPartyIdAndApplicantId(request.getPartyId(), userId)
                        .map(com.example.mate.entity.PartyApplication::getIsApproved)
                        .orElse(false);

        if (!isMember) {
            throw new UnauthorizedAccessException("파티 참여자만 메시지를 보낼 수 있습니다.");
        }

        String userName = userRepository.findById(userId)
                .map(com.example.auth.entity.UserEntity::getName)
                .orElse("Unknown");

        ChatMessage chatMessage = ChatMessage.builder()
                .partyId(request.getPartyId())
                .senderId(userId)
                .senderName(userName)
                .message(request.getMessage())
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        return ChatMessageDTO.Response.from(savedMessage);
    }

    // 파티별 채팅 메시지 조회
    @Transactional(readOnly = true)
    public List<ChatMessageDTO.Response> getMessagesByPartyId(Long partyId, Principal principal) {
        assertPartyMember(partyId, principal);
        return chatMessageRepository.findByPartyIdOrderByCreatedAtAsc(partyId).stream()
                .map(ChatMessageDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 파티별 최근 메시지 조회
    @Transactional(readOnly = true)
    public ChatMessageDTO.Response getLatestMessage(Long partyId, Principal principal) {
        assertPartyMember(partyId, principal);
        ChatMessage message = chatMessageRepository.findTopByPartyIdOrderByCreatedAtDesc(partyId);
        return message != null ? ChatMessageDTO.Response.from(message) : null;
    }

    private void assertPartyMember(Long partyId, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("로그인이 필요합니다.");
        }
        Long userId = userService.getUserIdByEmail(principal.getName());
        var party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        boolean isMember = party.getHostId().equals(userId)
                || applicationRepository.findByPartyIdAndApplicantId(partyId, userId)
                .map(com.example.mate.entity.PartyApplication::getIsApproved)
                .orElse(false);
        if (!isMember) {
            throw new UnauthorizedAccessException("파티 참여자만 채팅을 조회할 수 있습니다.");
        }
    }
}
