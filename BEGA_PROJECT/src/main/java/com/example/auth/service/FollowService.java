package com.example.auth.service;

import com.example.auth.dto.FollowCountResponse;
import com.example.auth.dto.FollowToggleResponse;
import com.example.auth.dto.UserFollowSummaryDto;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserFollow;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.config.CurrentUser;
import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final UserFollowRepository followRepo;
    private final UserBlockRepository blockRepo;
    private final UserRepository userRepo;
    private final CurrentUser currentUser;
    private final NotificationService notificationService;

    /**
     * 팔로우 토글 (팔로우/언팔로우)
     */
    @Transactional
    public FollowToggleResponse toggleFollow(Long targetUserId) {
        UserEntity me = currentUser.get();

        // 자기 자신 팔로우 방지
        if (me.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신을 팔로우할 수 없습니다.");
        }

        // [NEW] 차단 관계 확인
        if (blockRepo.existsBidirectionalBlock(me.getId(), targetUserId)) {
            throw new IllegalStateException("차단 관계가 있어 팔로우할 수 없습니다.");
        }

        // 대상 유저 존재 확인
        UserEntity target = userRepo.findById(Objects.requireNonNull(targetUserId))
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        UserFollow.Id followId = new UserFollow.Id(me.getId(), targetUserId);

        boolean following;
        boolean notifyNewPosts = false;

        if (followRepo.existsById(followId)) {
            // 이미 팔로우 중이면 언팔로우
            followRepo.deleteById(followId);
            following = false;
        } else {
            // 팔로우
            UserFollow follow = new UserFollow();
            follow.setId(followId);
            follow.setFollower(me);
            follow.setFollowing(target);
            follow.setNotifyNewPosts(false); // 기본값: 알림 끔
            followRepo.save(follow);
            following = true;

            // 새 팔로워 알림 전송
            notificationService.createNotification(
                    targetUserId,
                    Notification.NotificationType.NEW_FOLLOWER,
                    "새 팔로워",
                    me.getName() + "님이 회원님을 팔로우하기 시작했습니다.",
                    me.getId());
        }

        long followerCount = followRepo.countByFollowingId(targetUserId);
        long followingCount = followRepo.countByFollowerId(targetUserId);

        return FollowToggleResponse.builder()
                .following(following)
                .notifyNewPosts(notifyNewPosts)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .build();
    }

    /**
     * 알림 설정 변경
     */
    @Transactional
    public FollowToggleResponse updateNotifySetting(Long targetUserId, boolean notifyNewPosts) {
        UserEntity me = currentUser.get();

        UserFollow follow = followRepo.findByFollowerIdAndFollowingId(me.getId(), targetUserId)
                .orElseThrow(() -> new NoSuchElementException("팔로우 관계가 없습니다."));

        follow.setNotifyNewPosts(notifyNewPosts);
        followRepo.save(follow);

        long followerCount = followRepo.countByFollowingId(targetUserId);
        long followingCount = followRepo.countByFollowerId(targetUserId);

        return FollowToggleResponse.builder()
                .following(true)
                .notifyNewPosts(notifyNewPosts)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .build();
    }

    /**
     * 팔로우 카운트 및 상태 조회
     */
    @Transactional(readOnly = true)
    public FollowCountResponse getFollowCounts(Long userId) {
        UserEntity me = currentUser.getOrNull();

        long followerCount = followRepo.countByFollowingId(userId);
        long followingCount = followRepo.countByFollowerId(userId);
        boolean isFollowedByMe = false;
        boolean notifyNewPosts = false;
        boolean blockedByMe = false;
        boolean blockingMe = false;

        if (me != null && !me.getId().equals(userId)) {
            UserFollow.Id followId = new UserFollow.Id(me.getId(), userId);
            isFollowedByMe = followRepo.existsById(followId);

            if (isFollowedByMe) {
                notifyNewPosts = followRepo.findByFollowerIdAndFollowingId(me.getId(), userId)
                        .map(UserFollow::getNotifyNewPosts)
                        .orElse(false);
            }

            // [NEW] 차단 상태 조회
            blockedByMe = blockRepo.existsById(new com.example.auth.entity.UserBlock.Id(me.getId(), userId));
            blockingMe = blockRepo.existsById(new com.example.auth.entity.UserBlock.Id(userId, me.getId()));
        }

        return FollowCountResponse.builder()
                .followerCount(followerCount)
                .followingCount(followingCount)
                .isFollowedByMe(isFollowedByMe)
                .notifyNewPosts(notifyNewPosts)
                .blockedByMe(blockedByMe)
                .blockingMe(blockingMe)
                .build();
    }

    /**
     * 내가 팔로우하는 유저 ID 목록 (게시글 필터링용)
     */
    @Transactional(readOnly = true)
    public List<Long> getFollowingIds(Long userId) {
        return followRepo.findFollowingIdsByFollowerId(userId);
    }

    /**
     * 나를 팔로우하고 알림 설정이 켜진 유저 ID 목록 (새 글 알림용)
     */
    @Transactional(readOnly = true)
    public List<Long> getFollowersWithNotifyEnabled(Long userId) {
        return followRepo.findFollowerIdsWithNotifyEnabled(userId);
    }

    /**
     * 팔로우 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepo.existsById(new UserFollow.Id(followerId, followingId));
    }

    /**
     * 팔로워 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<UserFollowSummaryDto> getFollowers(Long userId, Pageable pageable) {
        UserEntity me = currentUser.getOrNull();
        Page<UserEntity> followers = followRepo.findFollowersByFollowingId(userId, pageable);

        List<Long> followerIds = followers.getContent().stream()
                .map(UserEntity::getId).toList();

        Set<Long> myFollowingIds = new HashSet<>();
        if (me != null && !followerIds.isEmpty()) {
            myFollowingIds = new HashSet<>(
                    followRepo.findFollowingIdsInList(me.getId(), followerIds));
        }

        final Set<Long> finalMyFollowingIds = myFollowingIds;
        return followers.map(user -> UserFollowSummaryDto.from(user, finalMyFollowingIds.contains(user.getId())));
    }

    /**
     * 팔로잉 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<UserFollowSummaryDto> getFollowing(Long userId, Pageable pageable) {
        UserEntity me = currentUser.getOrNull();
        Page<UserEntity> following = followRepo.findFollowingByFollowerId(userId, pageable);

        List<Long> followingIds = following.getContent().stream()
                .map(UserEntity::getId).toList();

        Set<Long> myFollowingIds = new HashSet<>();
        if (me != null && !followingIds.isEmpty()) {
            myFollowingIds = new HashSet<>(
                    followRepo.findFollowingIdsInList(me.getId(), followingIds));
        }

        final Set<Long> finalMyFollowingIds = myFollowingIds;
        return following.map(user -> UserFollowSummaryDto.from(user, finalMyFollowingIds.contains(user.getId())));
    }

    /**
     * 팔로워 삭제 (상대방이 나를 팔로우하는 관계 삭제)
     */
    @Transactional
    public void removeFollower(Long followerId) {
        UserEntity me = currentUser.get();
        UserFollow.Id followId = new UserFollow.Id(followerId, me.getId());

        if (!followRepo.existsById(followId)) {
            throw new NoSuchElementException("해당 팔로워가 없습니다.");
        }

        followRepo.deleteById(followId);
    }

    /**
     * 양방향 팔로우 관계 삭제 (차단 시 사용)
     */
    @Transactional
    public void removeBidirectionalFollow(Long userId1, Long userId2) {
        // userId1 -> userId2 관계 삭제
        followRepo.findByFollowerIdAndFollowingId(userId1, userId2)
                .ifPresent(followRepo::delete);

        // userId2 -> userId1 관계 삭제
        followRepo.findByFollowerIdAndFollowingId(userId2, userId1)
                .ifPresent(followRepo::delete);
    }
}
