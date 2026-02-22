package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheerCommentLikeRepo extends JpaRepository<CheerCommentLike, CheerCommentLike.Id> {

    /**
     * 특정 사용자가 좋아요한 댓글 ID 목록 조회 (일괄 처리용)
     * N+1 문제 해결: 댓글 목록에서 한 번의 쿼리로 좋아요 여부 확인
     */
    @Query("SELECT cl.id.commentId FROM CheerCommentLike cl WHERE cl.id.userId = :userId AND cl.id.commentId IN :commentIds")
    List<Long> findLikedCommentIdsByUserIdAndCommentIdIn(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);

    /**
     * 특정 사용자가 특정 댓글에 좋아요했는지 확인
     */
    boolean existsByIdCommentIdAndIdUserId(Long commentId, Long userId);
}
