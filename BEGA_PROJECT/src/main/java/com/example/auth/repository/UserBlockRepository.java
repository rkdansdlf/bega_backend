package com.example.auth.repository;

import com.example.auth.entity.UserBlock;
import com.example.auth.entity.UserBlock.Id;
import com.example.auth.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Id> {

    // 차단 관계 존재 여부 확인
    boolean existsById(@org.springframework.lang.NonNull Id id);

    // 내가 차단한 유저 목록 (페이징)
    @Query("SELECT ub.blocked FROM UserBlock ub WHERE ub.blocker.id = :userId ORDER BY ub.createdAt DESC")
    Page<UserEntity> findBlockedByBlockerId(@Param("userId") Long userId, Pageable pageable);

    // 내가 차단한 유저 ID 목록 (게시글 필터링용)
    @Query("SELECT ub.blocked.id FROM UserBlock ub WHERE ub.blocker.id = :userId")
    List<Long> findBlockedIdsByBlockerId(@Param("userId") Long userId);

    // 나를 차단한 유저 ID 목록 (댓글/좋아요 제한용)
    @Query("SELECT ub.blocker.id FROM UserBlock ub WHERE ub.blocked.id = :userId")
    List<Long> findBlockerIdsByBlockedId(@Param("userId") Long userId);

    // 차단 수
    @Query("SELECT COUNT(ub) FROM UserBlock ub WHERE ub.id.blockerId = :blockerId")
    long countByBlockerId(@Param("blockerId") Long blockerId);

    // 양방향 차단 관계 확인 (상대방이 나를 차단했는지 또는 내가 상대방을 차단했는지)
    @Query("SELECT CASE WHEN COUNT(ub) > 0 THEN true ELSE false END FROM UserBlock ub " +
            "WHERE (ub.id.blockerId = :userId1 AND ub.id.blockedId = :userId2) " +
            "OR (ub.id.blockerId = :userId2 AND ub.id.blockedId = :userId1)")
    boolean existsBidirectionalBlock(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
