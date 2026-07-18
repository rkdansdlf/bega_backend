package com.example.mate.service;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.UserService;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.realtime.RealtimeOutboxWriter;
import com.example.media.service.MediaLinkService;
import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.entity.ChatMessage;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private RealtimeOutboxWriter realtimeOutboxWriter;

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
        when(partyRepository.findAccessibleByIdAndParticipantId(55L, 77L)).thenReturn(Optional.of(party));
        when(chatMessageRepository.findByPartyIdAndSenderIdAndClientMessageId(55L, 77L, "client-msg-1"))
                .thenReturn(Optional.of(existingMessage));

        ChatMessageDTO.Response response = chatMessageService.sendMessage(ChatMessageDTO.Request.builder()
                .partyId(55L)
                .message("중복 방지 테스트 메시지")
                .clientMessageId("client-msg-1")
                .build(), principal);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getClientMessageId()).isEqualTo("client-msg-1");
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verifyNoInteractions(realtimeOutboxWriter);
    }

    @Test
    @DisplayName("sendMessage allows an approved participant")
    void sendMessage_approvedParticipantCanSend() {
        Party party = Party.builder()
                .id(56L)
                .hostId(77L)
                .status(Party.PartyStatus.MATCHED)
                .build();

        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 88L)).thenReturn(Optional.of(party));
        when(userRepository.findById(88L)).thenReturn(Optional.of(com.example.auth.entity.UserEntity.builder()
                .id(88L)
                .name("Guest")
                .build()));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(101L);
            return message;
        });

        ChatMessageDTO.Response response = chatMessageService.sendMessage(ChatMessageDTO.Request.builder()
                .partyId(56L)
                .message("승인된 참여자 메시지")
                .build(), 88L);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getSenderId()).isEqualTo(88L);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(realtimeOutboxWriter).broadcast("/topic/party/56", response);
    }

    @Test
    @DisplayName("sendMessage rejects another user's legacy chat image key before saving")
    void sendMessage_rejectsInvalidImageReferenceBeforeSaving() {
        Party party = Party.builder()
                .id(56L)
                .hostId(77L)
                .status(Party.PartyStatus.MATCHED)
                .build();
        String imageUrl = "chat/77/secret.webp";

        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 88L)).thenReturn(Optional.of(party));
        when(userRepository.findById(88L)).thenReturn(Optional.of(com.example.auth.entity.UserEntity.builder()
                .id(88L)
                .name("Guest")
                .build()));
        when(chatImageService.normalizeChatStoragePathForUser(88L, imageUrl))
                .thenThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "invalid image"));

        assertThatThrownBy(() -> chatMessageService.sendMessage(ChatMessageDTO.Request.builder()
                .partyId(56L)
                .message("이미지 메시지")
                .imageUrl(imageUrl)
                .build(), 88L))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("MEDIA_ASSET_NOT_FOUND"));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verifyNoInteractions(mediaLinkService);
    }

    @Test
    @DisplayName("sendMessage stores canonical chat image storage path")
    void sendMessage_storesCanonicalImagePath() {
        Party party = Party.builder()
                .id(56L)
                .hostId(77L)
                .status(Party.PartyStatus.MATCHED)
                .build();
        String imageUrl = "media/chat/88/asset.webp";

        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 88L)).thenReturn(Optional.of(party));
        when(userRepository.findById(88L)).thenReturn(Optional.of(com.example.auth.entity.UserEntity.builder()
                .id(88L)
                .name("Guest")
                .build()));
        when(chatImageService.normalizeChatStoragePathForUser(88L, imageUrl)).thenReturn(imageUrl);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(102L);
            return message;
        });
        when(chatImageService.resolveChatImageUrlForUser(88L, imageUrl))
                .thenReturn("https://signed.example/media/chat/88/asset.webp");

        ChatMessageDTO.Response response = chatMessageService.sendMessage(ChatMessageDTO.Request.builder()
                .partyId(56L)
                .message("이미지 메시지")
                .imageUrl(imageUrl)
                .build(), 88L);

        assertThat(response.getImageUrl()).isEqualTo("https://signed.example/media/chat/88/asset.webp");
        verify(mediaLinkService).resolveReadyAssets(88L, com.example.media.entity.MediaDomain.CHAT, List.of(imageUrl));
        verify(mediaLinkService).syncChatLink(102L, 88L, imageUrl);
    }

    @Test
    @DisplayName("sendMessage treats a non-participant party as not found before saving")
    void sendMessage_nonParticipantIsTreatedAsNotFoundBeforeSaving() {
        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.sendMessage(ChatMessageDTO.Request.builder()
                .partyId(56L)
                .message("권한 없는 메시지")
                .build(), 99L))
                .isInstanceOf(PartyNotFoundException.class);

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verifyNoInteractions(mediaLinkService);
    }

    @Test
    @DisplayName("getMessagesByPartyId treats a non-participant party as not found")
    void getMessagesByPartyId_nonParticipantIsTreatedAsNotFound() {
        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.getMessagesByPartyId(56L, 99L))
                .isInstanceOf(PartyNotFoundException.class);

        verify(chatMessageRepository, never()).findByPartyIdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    @DisplayName("getMessagesByPartyId returns the bounded latest page in chronological order")
    void getMessagesByPartyId_returnsLatestPageInChronologicalOrder() {
        Party party = Party.builder()
                .id(56L)
                .hostId(77L)
                .status(Party.PartyStatus.MATCHED)
                .build();
        ChatMessage newest = ChatMessage.builder()
                .id(103L)
                .partyId(56L)
                .senderId(77L)
                .senderName("Host")
                .message("three")
                .createdAt(Instant.parse("2026-03-10T09:02:00Z"))
                .build();
        ChatMessage middle = ChatMessage.builder()
                .id(102L)
                .partyId(56L)
                .senderId(77L)
                .senderName("Host")
                .message("two")
                .createdAt(Instant.parse("2026-03-10T09:01:00Z"))
                .build();
        ChatMessage oldest = ChatMessage.builder()
                .id(101L)
                .partyId(56L)
                .senderId(77L)
                .senderName("Host")
                .message("one")
                .createdAt(Instant.parse("2026-03-10T09:00:00Z"))
                .build();

        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 77L)).thenReturn(Optional.of(party));
        when(chatMessageRepository.findByPartyIdOrderByIdDesc(eq(56L), any(Pageable.class)))
                .thenReturn(List.of(newest, middle, oldest));

        List<ChatMessageDTO.Response> responses = chatMessageService.getMessagesByPartyId(56L, 77L, 3, null);

        assertThat(responses).extracting(ChatMessageDTO.Response::getId)
                .containsExactly(101L, 102L, 103L);
        verify(chatMessageRepository).findByPartyIdOrderByIdDesc(eq(56L), any(Pageable.class));
    }

    @Test
    @DisplayName("getMessagesByPartyId uses beforeId for older pages")
    void getMessagesByPartyId_usesBeforeIdForOlderPage() {
        Party party = Party.builder()
                .id(56L)
                .hostId(77L)
                .status(Party.PartyStatus.MATCHED)
                .build();
        ChatMessage older = ChatMessage.builder()
                .id(99L)
                .partyId(56L)
                .senderId(77L)
                .senderName("Host")
                .message("older")
                .createdAt(Instant.parse("2026-03-10T08:59:00Z"))
                .build();

        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 77L)).thenReturn(Optional.of(party));
        when(chatMessageRepository.findByPartyIdAndIdLessThanOrderByIdDesc(eq(56L), eq(100L), any(Pageable.class)))
                .thenReturn(List.of(older));

        List<ChatMessageDTO.Response> responses = chatMessageService.getMessagesByPartyId(56L, 77L, 50, 100L);

        assertThat(responses).extracting(ChatMessageDTO.Response::getId).containsExactly(99L);
        verify(chatMessageRepository).findByPartyIdAndIdLessThanOrderByIdDesc(eq(56L), eq(100L), any(Pageable.class));
    }

    @Test
    @DisplayName("updateChatReadTimestamp updates host read timestamp")
    void updateChatReadTimestamp_hostCanMarkRead() {
        Party party = Party.builder()
                .id(56L)
                .hostId(77L)
                .build();

        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 77L)).thenReturn(Optional.of(party));

        chatMessageService.updateChatReadTimestamp(56L, 77L);

        assertThat(party.getHostLastReadChatAt()).isNotNull();
        verify(applicationRepository, never()).findByPartyIdAndApplicantId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("updateChatReadTimestamp updates approved participant read timestamp")
    void updateChatReadTimestamp_approvedParticipantCanMarkRead() {
        Party party = Party.builder()
                .id(56L)
                .hostId(77L)
                .build();
        PartyApplication application = PartyApplication.builder()
                .partyId(56L)
                .applicantId(88L)
                .isApproved(true)
                .build();

        when(partyRepository.findAccessibleByIdAndParticipantId(56L, 88L)).thenReturn(Optional.of(party));
        when(applicationRepository.findByPartyIdAndApplicantId(56L, 88L)).thenReturn(Optional.of(application));

        chatMessageService.updateChatReadTimestamp(56L, 88L);

        assertThat(application.getLastReadChatAt()).isNotNull();
    }
}
