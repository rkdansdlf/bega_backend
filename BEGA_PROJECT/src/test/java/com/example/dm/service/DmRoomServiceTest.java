package com.example.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.example.dm.dto.DmRoomDto;
import com.example.dm.entity.DmRoom;
import com.example.dm.repository.DmRoomRepository;
import com.example.profile.storage.service.ProfileImageService;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmRoomService tests")
class DmRoomServiceTest {

    @Mock
    private DmRoomRepository dmRoomRepository;

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

        when(userRepository.findByHandle("@target")).thenReturn(Optional.of(target));
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

        when(userRepository.findByHandle("@self")).thenReturn(Optional.of(self));

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

        when(userRepository.findByHandle("@target")).thenReturn(Optional.of(target));
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
    @DisplayName("block relationship rejects room bootstrap even when room exists")
    void bootstrapRoom_rejectsBlockedRelationship() {
        UserEntity target = targetUser();

        when(userRepository.findByHandle("@target")).thenReturn(Optional.of(target));
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

        when(userRepository.findByHandle("@target")).thenReturn(Optional.of(target));
        when(userBlockRepository.existsBidirectionalBlock(10L, 20L)).thenReturn(false);
        when(dmRoomRepository.findByParticipantOneIdAndParticipantTwoId(10L, 20L)).thenReturn(Optional.of(existingRoom));

        DmRoomDto.BootstrapResponse response = dmRoomService.bootstrapRoom(10L, DmRoomDto.BootstrapRequest.builder()
                .targetHandle("@target")
                .build());

        assertThat(response.getRoomId()).isEqualTo(88L);
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
