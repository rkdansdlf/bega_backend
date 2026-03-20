package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerComment;
import com.example.auth.entity.UserEntity;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CheerCommentRepo extends JpaRepository<CheerComment, Long> {
    @EntityGraph(attributePaths = "author")
    Page<CheerComment> findByPostIdOrderByCreatedAtDesc(Long postId, Pageable pageable);

    /**
     * 최상위 댓글만 조회 (parentComment가 null인 댓글)
     * EntityGraph로 댓글 트리 전체를 eager 로딩 (최대 3단계까지)
     */
    @EntityGraph(attributePaths = {
        "author", 
        "replies", 
        "replies.author",
        "replies.replies",
        "replies.replies.author"
    })
    Page<CheerComment> findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(Long postId, Pageable pageable);

    /**
     * 특정 게시글의 모든 댓글 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CheerComment c WHERE c.post.id = :postId")
    void deleteByPostId(Long postId);

    @Query("SELECT c.likeCount FROM CheerComment c WHERE c.id = :commentId")
    Integer findLikeCountById(@Param("commentId") Long commentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheerComment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :commentId")
    void incrementLikeCount(@Param("commentId") Long commentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheerComment c SET c.likeCount = CASE WHEN c.likeCount > 0 THEN c.likeCount - 1 ELSE 0 END WHERE c.id = :commentId")
    void decrementLikeCount(@Param("commentId") Long commentId);
    
    /**
     * 특정 게시글의 전체 댓글 수 조회 (댓글 + 대댓글 모두 포함)
     */
    @Query("SELECT COUNT(c) FROM CheerComment c WHERE c.post.id = :postId")
    Long countByPostId(@Param("postId") Long postId);
    
    /**
     * 특정 게시글의 모든 댓글을 페치 조인으로 조회 (N+1 문제 해결)
     * 댓글 트리 전체를 한 번의 쿼리로 로딩
     */
    @Query("SELECT DISTINCT c FROM CheerComment c " +
           "LEFT JOIN FETCH c.author " +
           "LEFT JOIN FETCH c.replies r " +
           "LEFT JOIN FETCH r.author " +
           "WHERE c.post.id = :postId AND c.parentComment IS NULL " +
           "ORDER BY c.createdAt DESC")
    List<CheerComment> findCommentsWithRepliesByPostId(@Param("postId") Long postId);

    /**
     * 댓글 ID 리스트로 댓글 트리를 일괄 로딩 (페이지네이션 후 상세 로딩용)
     */
    @EntityGraph(attributePaths = {
        "author",
        "replies",
        "replies.author",
        "replies.replies",
        "replies.replies.author"
    })
    @Query("SELECT DISTINCT c FROM CheerComment c WHERE c.id IN :commentIds ORDER BY c.createdAt DESC")
    List<CheerComment> findWithRepliesByIdIn(@Param("commentIds") List<Long> commentIds);
    
    List<CheerComment> findByAuthor(UserEntity author);

    @Query("""
            SELECT c.content
            FROM CheerComment c
            WHERE c.post.id = :postId
              AND c.author.id = :authorId
              AND c.parentComment IS NULL
              AND c.createdAt > :since
            """)
    List<String> findRecentTopLevelContentsByPostIdAndAuthorIdAndCreatedAtAfter(
            @Param("postId") Long postId,
            @Param("authorId") Long authorId,
            @Param("since") Instant since);

    @Query("""
            SELECT c.content
            FROM CheerComment c
            WHERE c.post.id = :postId
              AND c.author.id = :authorId
              AND c.parentComment.id = :parentCommentId
              AND c.createdAt > :since
            """)
    List<String> findRecentReplyContentsByPostIdAndAuthorIdAndParentCommentIdAndCreatedAtAfter(
            @Param("postId") Long postId,
            @Param("authorId") Long authorId,
            @Param("parentCommentId") Long parentCommentId,
            @Param("since") Instant since);
}
