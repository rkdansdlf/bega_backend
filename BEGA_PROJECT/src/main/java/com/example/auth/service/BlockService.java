package com.example.auth.service;

import com.example.auth.dto.BlockToggleResponse;
import com.example.auth.dto.UserFollowSummaryDto;
import com.example.auth.entity.UserBlock;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository blockRepo;
    private final UserRepository userRepo;
    private final FollowService followService;
    private final CurrentUser currentUser;

    /**
     * 차단 토글 (차단/차단해제)
     */
    @Transactional
    public BlockToggleResponse toggleBlock(Long targetUserId) {
        UserEntity me = currentUser.get();

        // 자기 자신 차단 방지
        if (me.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신을 차단할 수 없습니다.");
        }

        // 대상 유저 존재 확인
        UserEntity target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        UserBlock.Id blockId = new UserBlock.Id(me.getId(), targetUserId);

        boolean blocked;

        if (blockRepo.existsById(blockId)) {
            // 이미 차단 중이면 차단 해제
            blockRepo.deleteById(blockId);
            blocked = false;
        } else {
            // 차단
            UserBlock block = new UserBlock();
            block.setId(blockId);
            block.setBlocker(me);
            block.setBlocked(target);
            blockRepo.save(block);
            blocked = true;

            // 양방향 팔로우 관계 삭제
            followService.removeBidirectionalFollow(me.getId(), targetUserId);
        }

        long blockedCount = blockRepo.countByBlockerId(me.getId());

        return BlockToggleResponse.builder()
                .blocked(blocked)
                .blockedCount(blockedCount)
                .build();
    }

    /**
     * 차단 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(Long blockerId, Long blockedId) {
        return blockRepo.existsById(new UserBlock.Id(blockerId, blockedId));
    }

    /**
     * 양방향 차단 여부 확인 (상대방이 나를 차단했거나, 내가 상대방을 차단한 경우)
     */
    @Transactional(readOnly = true)
    public boolean hasBidirectionalBlock(Long userId1, Long userId2) {
        return blockRepo.existsBidirectionalBlock(userId1, userId2);
    }

    /**
     * 내가 차단한 유저 ID 목록 (게시글 필터링용)
     */
    @Transactional(readOnly = true)
    public List<Long> getBlockedIds(Long userId) {
        return blockRepo.findBlockedIdsByBlockerId(userId);
    }

    /**
     * 나를 차단한 유저 ID 목록
     */
    @Transactional(readOnly = true)
    public List<Long> getBlockerIds(Long userId) {
        return blockRepo.findBlockerIdsByBlockedId(userId);
    }

    /**
     * 내가 차단한 유저 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<UserFollowSummaryDto> getBlockedUsers(Pageable pageable) {
        UserEntity me = currentUser.get();
        Page<UserEntity> blockedUsers = blockRepo.findBlockedByBlockerId(me.getId(), pageable);

        return blockedUsers.map(user -> UserFollowSummaryDto.from(user, false));
    }
}
