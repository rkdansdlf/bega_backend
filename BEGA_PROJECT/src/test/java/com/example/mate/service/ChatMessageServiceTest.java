package com.example.mate.service;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.UserService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
