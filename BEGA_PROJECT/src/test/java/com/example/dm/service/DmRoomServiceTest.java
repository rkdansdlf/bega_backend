package com.example.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ForbiddenBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.dm.dto.DmRoomDto;
import com.example.dm.entity.DmMessage;
import com.example.dm.entity.DmRoom;
import com.example.dm.repository.DmMessageRepository;
import com.example.dm.repository.DmRoomRepository;
import com.example.profile.storage.service.ProfileImageService;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmRoomService tests")
class DmRoomServiceTest {

    @Mock
    private DmRoomRepository dmRoomRepository;

    @Mock
    private DmMessageRepository dmMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private ProfileImageService profileImageService;

    @InjectMocks
    private DmRoomService dmRoomService;

    @Test
    @DisplayName("same user pair always resolves to the existing room")
    void bootstrapRoom_reusesExistingRoom() {
        UserEntity target = targetUser();
        DmRoom existingRoom = DmRoom.builder()
                .id(88L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();

        when(userRepository.findByHandleWithFavoriteTeam("@target")).thenReturn(Optional.of(target));
        when(userBlockRepository.existsBidirectionalBlock(10L, 20L)).thenReturn(false);
        when(dmRoomRepository.findByParticipantOneIdAndParticipantTwoId(10L, 20L)).thenReturn(Optional.of(existingRoom));

        DmRoomDto.BootstrapResponse response = dmRoomService.bootstrapRoom(10L, DmRoomDto.BootstrapRequest.builder()
                .targetHandle("@target")
                .build());

        assertThat(response.getRoomId()).isEqualTo(88L);
        assertThat(response.getMembershipState()).isEqualTo(DmRoomService.MEMBERSHIP_ACTIVE);
        verify(dmRoomRepository).findByParticipantOneIdAndParticipantTwoId(10L, 20L);
    }

    @Test
    @DisplayName("self dm is rejected")
    void bootstrapRoom_rejectsSelfDm() {
        UserEntity self = UserEntity.builder()
                .id(10L)
                .uniqueId(UUID.randomUUID())
                .handle("@self")
                .name("Self")
                .email("self@example.com")
                .role("ROLE_USER")
                .build();

        when(userRepository.findByHandleWithFavoriteTeam("@self")).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> dmRoomService.bootstrapRoom(10L, DmRoomDto.BootstrapRequest.builder()
                .targetHandle("@self")
                .build()))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessage("본인과는 대화를 시작할 수 없습니다.");
    }

    @Test
    @DisplayName("new room creation requires current follow relationship")
    void bootstrapRoom_requiresFollowForNewRoom() {
        UserEntity target = targetUser();

        when(userRepository.findByHandleWithFavoriteTeam("@target")).thenReturn(Optional.of(target));
        when(userBlockRepository.existsBidirectionalBlock(10L, 20L)).thenReturn(false);
        when(dmRoomRepository.findByParticipantOneIdAndParticipantTwoId(10L, 20L)).thenReturn(Optional.empty());
        when(userFollowRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> dmRoomService.bootstrapRoom(10L, DmRoomDto.BootstrapRequest.builder()
                .targetHandle("@target")
                .build()))
                .isInstanceOf(ForbiddenBusinessException.class)
                .hasMessage("팔로우한 사용자에게만 메시지를 보낼 수 있습니다.");
    }

    @Test
    @DisplayName("new room creation flushes before resolving a competing bootstrap")
    void bootstrapRoom_createsRoomAfterFollowCheck() {
        UserEntity target = targetUser();
        DmRoom createdRoom = DmRoom.builder()
                .id(89L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();

        when(userRepository.findByHandleWithFavoriteTeam("@target")).thenReturn(Optional.of(target));
        when(userBlockRepository.existsBidirectionalBlock(10L, 20L)).thenReturn(false);
        when(dmRoomRepository.findByParticipantOneIdAndParticipantTwoId(10L, 20L)).thenReturn(Optional.empty());
        when(userFollowRepository.existsById(any())).thenReturn(true);
        when(dmRoomRepository.saveAndFlush(any())).thenReturn(createdRoom);

        DmRoomDto.BootstrapResponse response = dmRoomService.bootstrapRoom(10L, DmRoomDto.BootstrapRequest.builder()
                .targetHandle("@target")
                .build());

        assertThat(response.getRoomId()).isEqualTo(89L);
        verify(dmRoomRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("block relationship rejects room bootstrap even when room exists")
    void bootstrapRoom_rejectsBlockedRelationship() {
        UserEntity target = targetUser();

        when(userRepository.findByHandleWithFavoriteTeam("@target")).thenReturn(Optional.of(target));
        when(userBlockRepository.existsBidirectionalBlock(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> dmRoomService.bootstrapRoom(10L, DmRoomDto.BootstrapRequest.builder()
                .targetHandle("@target")
                .build()))
                .isInstanceOf(ForbiddenBusinessException.class)
                .hasMessage("차단 관계인 사용자와는 메시지를 주고받을 수 없습니다.");
    }

    @Test
    @DisplayName("existing room remains accessible after unfollow")
    void bootstrapRoom_allowsExistingRoomAfterUnfollow() {
        UserEntity target = targetUser();
        DmRoom existingRoom = DmRoom.builder()
                .id(88L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();

        when(userRepository.findByHandleWithFavoriteTeam("@target")).thenReturn(Optional.of(target));
        when(userBlockRepository.existsBidirectionalBlock(10L, 20L)).thenReturn(false);
        when(dmRoomRepository.findByParticipantOneIdAndParticipantTwoId(10L, 20L)).thenReturn(Optional.of(existingRoom));

        DmRoomDto.BootstrapResponse response = dmRoomService.bootstrapRoom(10L, DmRoomDto.BootstrapRequest.builder()
                .targetHandle("@target")
                .build());

        assertThat(response.getRoomId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("non participants cannot distinguish missing and inaccessible rooms")
    void getAccessibleRoom_treatsNonParticipantRoomAsNotFound() {
        when(dmRoomRepository.findAccessibleByIdAndParticipantId(88L, 30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dmRoomService.getAccessibleRoom(88L, 30L))
                .isInstanceOf(NotFoundBusinessException.class)
                .hasMessage("대화방을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("subscribe authorization requires a participant-scoped room match")
    void canSubscribe_requiresParticipantScopedRoom() {
        when(dmRoomRepository.findAccessibleByIdAndParticipantId(88L, 30L)).thenReturn(Optional.empty());

        assertThat(dmRoomService.canSubscribe(88L, 30L)).isFalse();
    }

    @Test
    @DisplayName("accessible participant can subscribe when neither user blocked the other")
    void canSubscribe_allowsAccessibleParticipantWithoutBidirectionalBlock() {
        DmRoom room = DmRoom.builder()
                .id(88L)
                .participantOneId(30L)
                .participantTwoId(40L)
                .build();
        when(dmRoomRepository.findAccessibleByIdAndParticipantId(88L, 30L)).thenReturn(Optional.of(room));
        when(userBlockRepository.existsBidirectionalBlock(30L, 40L)).thenReturn(false);

        assertThat(dmRoomService.canSubscribe(88L, 30L)).isTrue();

        verify(dmRoomRepository).findAccessibleByIdAndParticipantId(88L, 30L);
        verify(userBlockRepository).existsBidirectionalBlock(30L, 40L);
    }

    @Test
    @DisplayName("inbox unread state includes inbound messages after the watermark")
    void getMyRooms_usesUnreadAggregateWhenLatestMessageWasSentByCurrentUser() {
        DmRoom room = DmRoom.builder()
                .id(88L)
                .participantOneId(10L)
                .participantTwoId(20L)
                .build();
        DmMessage latestMessage = DmMessage.builder()
                .id(120L)
                .roomId(88L)
                .senderId(10L)
                .content("내가 보낸 마지막 메시지")
                .createdAt(Instant.parse("2026-10-01T10:03:00Z"))
                .build();
        when(dmRoomRepository.findAllByParticipantId(10L)).thenReturn(List.of(room));
        when(dmMessageRepository.findLatestMessagePerRoom(List.of(88L))).thenReturn(List.of(latestMessage));
        when(dmMessageRepository.findUnreadRoomIdsForParticipant(10L, List.of(88L))).thenReturn(List.of(88L));
        when(userRepository.findAllById(List.of(20L))).thenReturn(List.of(targetUser()));

        List<DmRoomDto.InboxItem> inbox = dmRoomService.getMyRooms(10L);

        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).isHasUnread()).isTrue();
        assertThat(inbox.get(0).getLastMessage().getContent()).isEqualTo("내가 보낸 마지막 메시지");
        verify(dmMessageRepository).findUnreadRoomIdsForParticipant(10L, List.of(88L));
    }

    @Test
    @DisplayName("accessible participant cannot subscribe when either user blocked the other")
    void canSubscribe_deniesAccessibleParticipantWithBidirectionalBlock() {
        DmRoom room = DmRoom.builder()
                .id(88L)
                .participantOneId(30L)
                .participantTwoId(40L)
                .build();
        when(dmRoomRepository.findAccessibleByIdAndParticipantId(88L, 30L)).thenReturn(Optional.of(room));
        when(userBlockRepository.existsBidirectionalBlock(30L, 40L)).thenReturn(true);

        assertThat(dmRoomService.canSubscribe(88L, 30L)).isFalse();

        verify(dmRoomRepository).findAccessibleByIdAndParticipantId(88L, 30L);
        verify(userBlockRepository).existsBidirectionalBlock(30L, 40L);
    }

    private UserEntity targetUser() {
        return UserEntity.builder()
                .id(20L)
                .uniqueId(UUID.randomUUID())
                .handle("@target")
                .name("Target")
                .email("target@example.com")
                .role("ROLE_USER")
                .build();
    }
}
