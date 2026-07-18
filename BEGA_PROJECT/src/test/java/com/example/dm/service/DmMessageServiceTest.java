package com.example.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.common.exception.NotFoundBusinessException;
import com.example.common.realtime.RealtimeOutboxWriter;
import com.example.dm.dto.DmMessageDto;
import com.example.dm.entity.DmMessage;
import com.example.dm.entity.DmRoom;
import com.example.dm.repository.DmMessageRepository;
import com.example.dm.repository.DmRoomRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmMessageService tests")
class DmMessageServiceTest {

    @Mock
    private DmMessageRepository dmMessageRepository;

    @Mock
    private DmRoomService dmRoomService;

    @Mock
    private DmRoomRepository dmRoomRepository;

    @Mock
    private RealtimeOutboxWriter realtimeOutboxWriter;

    @InjectMocks
    private DmMessageService dmMessageService;

    @Test
    @DisplayName("duplicate clientMessageId reuses the existing message row")
    void sendMessage_reusesExistingMessageForDuplicateClientMessageId() {
        DmRoom room = DmRoom.builder()
                .id(55L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();
        DmMessage existingMessage = DmMessage.builder()
                .id(101L)
                .roomId(55L)
                .senderId(10L)
                .content("안녕하세요")
                .clientMessageId("client-1")
                .build();

        when(dmRoomService.getAccessibleRoom(55L, 10L)).thenReturn(room);
        when(dmMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(55L, 10L, "client-1"))
                .thenReturn(java.util.Optional.of(existingMessage));

        DmMessageDto.Response response = dmMessageService.sendMessage(10L, DmMessageDto.Request.builder()
                .roomId(55L)
                .content("안녕하세요")
                .clientMessageId("client-1")
                .build());

        assertThat(response.getId()).isEqualTo(101L);
        verify(dmMessageRepository, never()).save(any(DmMessage.class));
        verify(realtimeOutboxWriter, never()).broadcast(any(), any());
    }

    @Test
    @DisplayName("new message stores an outbox event in the service transaction")
    void sendMessage_writesRealtimeOutboxEvent() {
        DmRoom room = DmRoom.builder()
                .id(55L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();
        when(dmRoomService.getAccessibleRoom(55L, 10L)).thenReturn(room);
        when(dmMessageRepository.save(any(DmMessage.class))).thenAnswer(invocation -> {
            DmMessage message = invocation.getArgument(0);
            message.setId(102L);
            return message;
        });

        DmMessageDto.Response response = dmMessageService.sendMessage(10L, DmMessageDto.Request.builder()
                .roomId(55L)
                .content("새 메시지")
                .build());

        verify(realtimeOutboxWriter).broadcast("/topic/dm/55", response);
    }

    @Test
    @DisplayName("message deletion stores a durable deletion event")
    void deleteMessage_writesRealtimeOutboxEvent() {
        DmMessage message = DmMessage.builder()
                .id(101L)
                .roomId(55L)
                .senderId(10L)
                .content("삭제할 메시지")
                .build();
        DmRoom room = DmRoom.builder()
                .id(55L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();
        when(dmMessageRepository.findById(101L)).thenReturn(Optional.of(message));
        when(dmRoomRepository.findAccessibleByIdAndParticipantId(55L, 10L)).thenReturn(Optional.of(room));

        Long roomId = dmMessageService.deleteMessage(101L, 10L);

        assertThat(roomId).isEqualTo(55L);
        verify(dmMessageRepository).delete(message);
        verify(realtimeOutboxWriter).broadcast(
                "/topic/dm/55",
                Map.of("messageId", 101L, "deleted", true, "roomId", 55L));
    }

    @Test
    @DisplayName("history rejects users who are not room participants")
    void getMessages_rejectsNonParticipants() {
        when(dmRoomService.getAccessibleRoom(55L, 30L))
                .thenThrow(new NotFoundBusinessException("DM_ROOM_NOT_FOUND", "대화방을 찾을 수 없습니다."));

        assertThatThrownBy(() -> dmMessageService.getMessages(55L, 30L))
                .isInstanceOf(NotFoundBusinessException.class)
                .hasMessage("대화방을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("history returns messages for participants")
    void getMessages_returnsMessagesForParticipants() {
        DmRoom room = DmRoom.builder()
                .id(55L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();
        DmMessage firstMessage = DmMessage.builder()
                .id(1L)
                .roomId(55L)
                .senderId(10L)
                .content("첫 메시지")
                .build();
        DmMessage secondMessage = DmMessage.builder()
                .id(2L)
                .roomId(55L)
                .senderId(20L)
                .content("응답")
                .build();

        when(dmRoomService.getAccessibleRoom(55L, 10L)).thenReturn(room);
        when(dmMessageRepository.findByRoomIdOrderByCreatedAtAsc(55L)).thenReturn(List.of(firstMessage, secondMessage));

        List<DmMessageDto.Response> messages = dmMessageService.getMessages(55L, 10L);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("첫 메시지");
        assertThat(messages.get(1).getContent()).isEqualTo("응답");
    }
}
