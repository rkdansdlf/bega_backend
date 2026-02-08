package com.example.auth.repository;

import com.example.auth.entity.UserFollow;
import com.example.auth.entity.UserFollow.Id;
import com.example.auth.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Id> {

        // 팔로우 관계 존재 여부 확인
        boolean existsById(@org.springframework.lang.NonNull Id id);

        // 내가 팔로우하는 유저 목록 (페이징)
        @Query("SELECT uf.following FROM UserFollow uf WHERE uf.follower.id = :userId ORDER BY uf.createdAt DESC")
        Page<UserEntity> findFollowingByFollowerId(@Param("userId") Long userId, Pageable pageable);

        // 나를 팔로우하는 유저 목록 (페이징)
        @Query("SELECT uf.follower FROM UserFollow uf WHERE uf.following.id = :userId ORDER BY uf.createdAt DESC")
        Page<UserEntity> findFollowersByFollowingId(@Param("userId") Long userId, Pageable pageable);

        // 내가 팔로우하는 유저 ID 목록 (게시글 필터링용)
        @Query("SELECT uf.following.id FROM UserFollow uf WHERE uf.follower.id = :userId")
        List<Long> findFollowingIdsByFollowerId(@Param("userId") Long userId);

        // 나를 팔로우하고 알림 설정이 켜진 유저 ID 목록 (새 글 알림용)
        @Query("SELECT uf.id.followerId FROM UserFollow uf WHERE uf.id.followingId = :userId AND uf.notifyNewPosts = true")
        List<Long> findFollowerIdsWithNotifyEnabled(@Param("userId") Long userId);

        // 팔로워 수
        @Query("SELECT COUNT(uf) FROM UserFollow uf WHERE uf.id.followingId = :followingId")
        long countByFollowingId(@Param("followingId") Long followingId);

        // 팔로잉 수
        @Query("SELECT COUNT(uf) FROM UserFollow uf WHERE uf.id.followerId = :followerId")
        long countByFollowerId(@Param("followerId") Long followerId);

        // 특정 팔로우 관계 조회 (알림 설정 변경용)
        @Query("SELECT uf FROM UserFollow uf WHERE uf.id.followerId = :followerId AND uf.id.followingId = :followingId")
        Optional<UserFollow> findByFollowerIdAndFollowingId(@Param("followerId") Long followerId,
                        @Param("followingId") Long followingId);

        // 여러 유저에 대한 팔로우 상태 일괄 확인 (UI 최적화용)
        @Query("SELECT uf.id.followingId FROM UserFollow uf WHERE uf.id.followerId = :followerId AND uf.id.followingId IN :followingIds")
        List<Long> findFollowingIdsInList(@Param("followerId") Long followerId,
                        @Param("followingIds") List<Long> followingIds);

        // 특정 유저의 모든 팔로우 관계 삭제 (차단 시 사용)
        @Modifying(clearAutomatically = true)
        @Query("DELETE FROM UserFollow uf WHERE uf.id.followerId = :userId OR uf.id.followingId = :userId")
        void deleteAllByUserId(@Param("userId") Long userId);

        // 양방향 팔로우 관계 삭제 (차단 시 사용)
        @Modifying(clearAutomatically = true)
        @Query("DELETE FROM UserFollow uf WHERE (uf.id.followerId = :userId1 AND uf.id.followingId = :userId2) OR (uf.id.followerId = :userId2 AND uf.id.followingId = :userId1)")
        void deleteBidirectionalFollow(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
