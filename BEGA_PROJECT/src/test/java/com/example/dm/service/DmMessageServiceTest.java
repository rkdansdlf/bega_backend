package com.example.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.common.exception.ForbiddenBusinessException;
import com.example.dm.dto.DmMessageDto;
import com.example.dm.entity.DmMessage;
import com.example.dm.entity.DmRoom;
import com.example.dm.repository.DmMessageRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmMessageService tests")
class DmMessageServiceTest {

    @Mock
    private DmMessageRepository dmMessageRepository;

    @Mock
    private DmRoomService dmRoomService;

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
    }

    @Test
    @DisplayName("history rejects users who are not room participants")
    void getMessages_rejectsNonParticipants() {
        when(dmRoomService.getAccessibleRoom(55L, 30L))
                .thenThrow(new ForbiddenBusinessException("DM_ACCESS_DENIED", "대화방 참여자만 접근할 수 있습니다."));

        assertThatThrownBy(() -> dmMessageService.getMessages(55L, 30L))
                .isInstanceOf(ForbiddenBusinessException.class)
                .hasMessage("대화방 참여자만 접근할 수 있습니다.");
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
