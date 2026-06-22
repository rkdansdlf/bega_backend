package com.example.dm.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserFollow;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.HandleNormalizer;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ForbiddenBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.dm.dto.DmRoomDto;
import com.example.dm.entity.DmMessage;
import com.example.dm.entity.DmRoom;
import com.example.dm.repository.DmMessageRepository;
import com.example.dm.repository.DmRoomRepository;
import com.example.profile.storage.service.ProfileImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DmRoomService {

    public static final String MEMBERSHIP_ACTIVE = "ACTIVE";

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserBlockRepository userBlockRepository;
    private final ProfileImageService profileImageService;

    @Transactional
    public DmRoomDto.BootstrapResponse bootstrapRoom(Long currentUserId, DmRoomDto.BootstrapRequest request) {
        long userId = requireCurrentUserId(currentUserId);
        UserEntity targetUser = findUserByHandleOrThrow(request.getTargetHandle());
        Long targetUserId = targetUser.getId();

        if (targetUserId == null) {
            throw new NotFoundBusinessException("DM_TARGET_NOT_FOUND", "대화 상대를 찾을 수 없습니다.");
        }

        if (targetUserId.equals(userId)) {
            throw new BadRequestBusinessException("DM_SELF_NOT_ALLOWED", "본인과는 대화를 시작할 수 없습니다.");
        }

        ensureNoBlock(userId, targetUserId);

        long participantOneId = Math.min(userId, targetUserId);
        long participantTwoId = Math.max(userId, targetUserId);

        Optional<DmRoom> existingRoom = dmRoomRepository.findByParticipantOneIdAndParticipantTwoId(
                participantOneId,
                participantTwoId);
        if (existingRoom.isPresent()) {
            return toBootstrapResponse(existingRoom.get(), targetUser);
        }

        if (!userFollowRepository.existsById(new UserFollow.Id(userId, targetUserId))) {
            throw new ForbiddenBusinessException("DM_FOLLOW_REQUIRED", "팔로우한 사용자에게만 메시지를 보낼 수 있습니다.");
        }

        DmRoom room = DmRoom.builder()
                .participantOneId(participantOneId)
                .participantTwoId(participantTwoId)
                .build();

        try {
            DmRoom savedRoom = dmRoomRepository.save(room);
            return toBootstrapResponse(savedRoom, targetUser);
        } catch (DataIntegrityViolationException e) {
            log.info("DM room already created concurrently for users {} and {}", participantOneId, participantTwoId);
            DmRoom resolvedRoom = dmRoomRepository.findByParticipantOneIdAndParticipantTwoId(participantOneId, participantTwoId)
                    .orElseThrow(() -> e);
            return toBootstrapResponse(resolvedRoom, targetUser);
        }
    }

    @Transactional(readOnly = true)
    public DmRoom getAccessibleRoom(Long roomId, Long currentUserId) {
        long userId = requireCurrentUserId(currentUserId);
        DmRoom room = dmRoomRepository.findAccessibleByIdAndParticipantId(roomId, userId)
                .orElseThrow(() -> new NotFoundBusinessException("DM_ROOM_NOT_FOUND", "대화방을 찾을 수 없습니다."));

        ensureNoBlock(userId, resolveTargetUserId(room, userId));
        return room;
    }

    @Transactional(readOnly = true)
    public boolean canSubscribe(Long roomId, Long currentUserId) {
        if (currentUserId == null || roomId == null) {
            return false;
        }

        return dmRoomRepository.findAccessibleByIdAndParticipantId(roomId, currentUserId)
                .filter(room -> !userBlockRepository.existsBidirectionalBlock(currentUserId, resolveTargetUserId(room, currentUserId)))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public DmRoomDto.TargetUser getTargetUser(Long roomId, Long currentUserId) {
        DmRoom room = getAccessibleRoom(roomId, currentUserId);
        return toTargetUser(findUserByIdOrThrow(resolveTargetUserId(room, currentUserId)));
    }

    public Long resolveTargetUserId(DmRoom room, Long currentUserId) {
        if (room.getParticipantOneId().equals(currentUserId)) {
            return room.getParticipantTwoId();
        }
        return room.getParticipantOneId();
    }

    @Transactional(readOnly = true)
    public List<DmRoomDto.InboxItem> getMyRooms(Long userId) {
        requireCurrentUserId(userId);
        List<DmRoom> rooms = dmRoomRepository.findAllByParticipantId(userId);
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(DmRoom::getId).toList();
        Map<Long, DmMessage> latestByRoom = dmMessageRepository.findLatestMessagePerRoom(roomIds)
                .stream()
                .collect(Collectors.toMap(DmMessage::getRoomId, Function.identity(), (a, b) -> a));

        List<Long> targetUserIds = rooms.stream()
                .map(r -> resolveTargetUserId(r, userId))
                .distinct()
                .toList();
        Map<Long, UserEntity> usersById = userRepository.findAllById(targetUserIds)
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return rooms.stream()
                .map(room -> {
                    Long targetUserId = resolveTargetUserId(room, userId);
                    UserEntity targetUser = usersById.get(targetUserId);
                    DmMessage lastMsg = latestByRoom.get(room.getId());
                    Instant myLastRead = room.getParticipantOneId().equals(userId)
                            ? room.getParticipantOneLastReadAt()
                            : room.getParticipantTwoLastReadAt();
                    boolean hasUnread = lastMsg != null
                            && !lastMsg.getSenderId().equals(userId)
                            && (myLastRead == null || lastMsg.getCreatedAt().isAfter(myLastRead));

                    return DmRoomDto.InboxItem.builder()
                            .roomId(room.getId())
                            .targetUser(targetUser != null ? toTargetUser(targetUser) : null)
                            .lastMessage(lastMsg != null
                                    ? DmRoomDto.InboxItem.LastMessagePreview.builder()
                                            .content(lastMsg.getContent())
                                            .createdAt(lastMsg.getCreatedAt())
                                            .senderId(lastMsg.getSenderId())
                                            .build()
                                    : null)
                            .hasUnread(hasUnread)
                            .build();
                })
                .sorted(Comparator.comparing(
                        item -> item.getLastMessage() != null ? item.getLastMessage().getCreatedAt() : Instant.EPOCH,
                        Comparator.reverseOrder()))
                .toList();
    }

    @Transactional
    public void markAsRead(Long roomId, Long userId) {
        dmRoomRepository.findAccessibleByIdAndParticipantId(roomId, userId).ifPresent(room -> {
            if (room.getParticipantOneId().equals(userId)) {
                room.setParticipantOneLastReadAt(Instant.now());
            } else {
                room.setParticipantTwoLastReadAt(Instant.now());
            }
            dmRoomRepository.save(room);
        });
    }

    private DmRoomDto.BootstrapResponse toBootstrapResponse(DmRoom room, UserEntity targetUser) {
        return DmRoomDto.BootstrapResponse.builder()
                .roomId(room.getId())
                .membershipState(MEMBERSHIP_ACTIVE)
                .targetUser(toTargetUser(targetUser))
                .build();
    }

    private DmRoomDto.TargetUser toTargetUser(UserEntity user) {
        return DmRoomDto.TargetUser.builder()
                .id(user.getId())
                .name(user.getName())
                .handle(user.getHandle())
                .favoriteTeam(user.getFavoriteTeamId())
                .profileImageUrl(resolveProfileImageUrl(user.getId(), user.getProfileImageUrl()))
                .build();
    }

    private String resolveProfileImageUrl(Long ownerUserId, String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }

        try {
            return profileImageService.getProfileImageUrlForUser(ownerUserId, profileImageUrl);
        } catch (Exception e) {
            log.warn("Failed to resolve DM target profile image: {}", e.getMessage());
            return null;
        }
    }

    private UserEntity findUserByHandleOrThrow(String handle) {
        return HandleNormalizer.candidates(handle).stream()
                .map(userRepository::findByHandle)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new NotFoundBusinessException("DM_TARGET_NOT_FOUND", "대화 상대를 찾을 수 없습니다."));
    }

    private UserEntity findUserByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundBusinessException("DM_TARGET_NOT_FOUND", "대화 상대를 찾을 수 없습니다."));
    }

    private void ensureNoBlock(Long currentUserId, Long targetUserId) {
        if (userBlockRepository.existsBidirectionalBlock(currentUserId, targetUserId)) {
            throw new ForbiddenBusinessException("DM_BLOCKED", "차단 관계인 사용자와는 메시지를 주고받을 수 없습니다.");
        }
    }

    private long requireCurrentUserId(Long currentUserId) {
        if (currentUserId == null) {
            throw new AuthenticationRequiredException("로그인이 필요합니다.");
        }
        return currentUserId;
    }
}
