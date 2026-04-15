package com.example.mate.service;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.UserService;
import com.example.media.service.MediaLinkService;
import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.entity.ChatMessage;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMessageService tests")
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyApplicationRepository applicationRepository;

    @Mock
    private ChatImageService chatImageService;

    @Mock
    private MediaLinkService mediaLinkService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("getTotalUnreadCount falls back to epoch when legacy timestamps are missing")
    void getTotalUnreadCount_usesEpochFallbackForLegacyRows() {
        Principal principal = () -> "host@example.com";
        Party hostedParty = Party.builder()
                .id(10L)
                .hostId(77L)
                .status(Party.PartyStatus.PENDING)
                .createdAt(null)
                .hostLastReadChatAt(null)
                .build();
        PartyApplication approvedApplication = PartyApplication.builder()
                .partyId(11L)
                .applicantId(77L)
                .isApproved(true)
                .createdAt(null)
                .lastReadChatAt(null)
                .build();

        when(userService.getUserIdByEmail("host@example.com")).thenReturn(77L);
        when(partyRepository.findByHostIdAndStatusIn(eq(77L), eq(List.of(
                Party.PartyStatus.PENDING,
                Party.PartyStatus.MATCHED,
                Party.PartyStatus.SELLING,
                Party.PartyStatus.CHECKED_IN))))
                .thenReturn(List.of(hostedParty));
        when(applicationRepository.findApprovedByApplicantIdAndPartyStatusIn(eq(77L), eq(List.of(
                Party.PartyStatus.PENDING,
                Party.PartyStatus.MATCHED,
                Party.PartyStatus.SELLING,
                Party.PartyStatus.CHECKED_IN))))
                .thenReturn(List.of(approvedApplication));
        when(chatMessageRepository.countByPartyIdAndCreatedAtAfterAndSenderIdNot(anyLong(), eq(Instant.EPOCH), eq(77L)))
                .thenReturn(2L, 3L);

        long unreadCount = chatMessageService.getTotalUnreadCount(principal);

        assertThat(unreadCount).isEqualTo(5L);
        verify(chatMessageRepository).countByPartyIdAndCreatedAtAfterAndSenderIdNot(10L, Instant.EPOCH, 77L);
        verify(chatMessageRepository).countByPartyIdAndCreatedAtAfterAndSenderIdNot(11L, Instant.EPOCH, 77L);
    }

    @Test
    @DisplayName("sendMessage reuses the existing row when clientMessageId already exists")
    void sendMessage_reusesExistingRowForDuplicateClientMessageId() {
        Principal principal = () -> "host@example.com";
        Party party = Party.builder()
                .id(55L)
                .hostId(77L)
                .status(Party.PartyStatus.MATCHED)
                .build();
        ChatMessage existingMessage = ChatMessage.builder()
                .id(100L)
                .partyId(55L)
                .senderId(77L)
                .senderName("Host")
                .message("중복 방지 테스트 메시지")
                .clientMessageId("client-msg-1")
                .createdAt(Instant.parse("2026-03-10T09:00:00Z"))
                .build();

        when(userService.getUserIdByEmail("host@example.com")).thenReturn(77L);
        when(partyRepository.findById(55L)).thenReturn(java.util.Optional.of(party));
        when(chatMessageRepository.findByPartyIdAndSenderIdAndClientMessageId(55L, 77L, "client-msg-1"))
                .thenReturn(java.util.Optional.of(existingMessage));

        ChatMessageDTO.Response response = chatMessageService.sendMessage(ChatMessageDTO.Request.builder()
                .partyId(55L)
                .message("중복 방지 테스트 메시지")
                .clientMessageId("client-msg-1")
                .build(), principal);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getClientMessageId()).isEqualTo("client-msg-1");
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }
}
