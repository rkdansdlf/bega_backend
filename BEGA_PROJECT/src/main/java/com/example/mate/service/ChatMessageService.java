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
import com.example.mate.entity.Party.PartyStatus;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PartyApplicationRepository applicationRepository;
    private final ChatImageService chatImageService;

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
                .imageUrl(request.getImageUrl())
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        return toResponseWithResolvedImage(savedMessage);
    }

    // 파티별 채팅 메시지 조회
    @Transactional(readOnly = true)
    public List<ChatMessageDTO.Response> getMessagesByPartyId(Long partyId, Principal principal) {
        assertPartyMember(partyId, principal);
        return chatMessageRepository.findByPartyIdOrderByCreatedAtAsc(partyId).stream()
                .map(this::toResponseWithResolvedImage)
                .collect(Collectors.toList());
    }

    // 파티별 최근 메시지 조회
    @Transactional(readOnly = true)
    public ChatMessageDTO.Response getLatestMessage(Long partyId, Principal principal) {
        assertPartyMember(partyId, principal);
        ChatMessage message = chatMessageRepository.findTopByPartyIdOrderByCreatedAtDesc(partyId);
        return message != null ? toResponseWithResolvedImage(message) : null;
    }

    private void assertPartyMember(Long partyId, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("로그인이 필요합니다.");
        }
        Long userId = userService.getUserIdByEmail(principal.getName());
        if (partyId == null) {
            throw new PartyNotFoundException(0L); // Or handle appropriately
        }
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

    // 채팅 읽음 처리
    @Transactional
    public void updateChatReadTimestamp(Long partyId, Principal principal) {
        if (partyId == null) {
            throw new PartyNotFoundException(0L);
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("로그인이 필요합니다.");
        }
        Long userId = userService.getUserIdByEmail(principal.getName());
        var party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        if (party.getHostId().equals(userId)) {
            party.setHostLastReadChatAt(java.time.Instant.now());
        } else {
            var app = applicationRepository.findByPartyIdAndApplicantId(partyId, userId)
                    .orElseThrow(() -> new UnauthorizedAccessException("파티 참여자가 아닙니다."));
            if (!app.getIsApproved()) {
                throw new UnauthorizedAccessException("파티 참여 승인이 필요합니다.");
            }
            app.setLastReadChatAt(java.time.Instant.now());
        }
    }

    // 전체 안 읽은 메시지 개수 조회
    @Transactional(readOnly = true)
    public long getTotalUnreadCount(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return 0;
        }
        Long userId = userService.getUserIdByEmail(principal.getName());

        List<PartyStatus> activeStatuses = List.of(
                PartyStatus.PENDING,
                PartyStatus.MATCHED,
                PartyStatus.SELLING,
                PartyStatus.CHECKED_IN);
        List<com.example.mate.entity.Party> hostedParties = partyRepository.findByHostIdAndStatusIn(userId, activeStatuses);
        List<com.example.mate.entity.PartyApplication> applications = applicationRepository
                .findApprovedByApplicantIdAndPartyStatusIn(userId, activeStatuses);

        long totalUnread = 0;

        for (com.example.mate.entity.Party party : hostedParties) {
            java.time.Instant lastRead = party.getHostLastReadChatAt();
            if (lastRead == null)
                lastRead = party.getCreatedAt();
            totalUnread += chatMessageRepository.countByPartyIdAndCreatedAtAfterAndSenderIdNot(
                    party.getId(),
                    lastRead,
                    userId);
        }

        for (com.example.mate.entity.PartyApplication app : applications) {
            java.time.Instant lastRead = app.getLastReadChatAt();
            if (lastRead == null)
                lastRead = app.getCreatedAt();
            totalUnread += chatMessageRepository.countByPartyIdAndCreatedAtAfterAndSenderIdNot(
                    app.getPartyId(),
                    lastRead,
                    userId);
        }

        return totalUnread;
    }

    private ChatMessageDTO.Response toResponseWithResolvedImage(ChatMessage chatMessage) {
        ChatMessageDTO.Response response = ChatMessageDTO.Response.from(chatMessage);
        String imageUrl = response.getImageUrl();
        if (imageUrl != null && !imageUrl.isBlank()) {
            response.setImageUrl(chatImageService.resolveChatImageUrl(imageUrl));
        }
        return response;
    }
}
