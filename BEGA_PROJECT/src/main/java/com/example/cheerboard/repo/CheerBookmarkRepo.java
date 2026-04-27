package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheerBookmarkRepo extends JpaRepository<CheerPostBookmark, CheerPostBookmark.Id> {
    interface PostBookmarkCount {
        Long getPostId();

        Long getBookmarkCount();
    }

    @EntityGraph(attributePaths = { "post", "post.author", "post.team", "post.repostOf", "post.repostOf.author",
            "post.repostOf.team" })
    Page<CheerPostBookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = { "post", "post.author", "post.team", "post.repostOf", "post.repostOf.author",
            "post.repostOf.team" })
    @Query("""
            SELECT b
            FROM CheerPostBookmark b
            WHERE b.user.id = :userId
              AND (
                    b.post.author.id = :viewerId
                    OR (
                        NOT EXISTS (
                            SELECT 1
                            FROM com.example.auth.entity.UserBlock ub
                            WHERE (ub.id.blockerId = :viewerId AND ub.id.blockedId = b.post.author.id)
                               OR (ub.id.blockerId = b.post.author.id AND ub.id.blockedId = :viewerId)
                        )
                        AND (
                            b.post.author.privateAccount = false
                            OR EXISTS (
                                SELECT 1
                                FROM com.example.auth.entity.UserFollow uf
                                WHERE uf.id.followerId = :viewerId
                                  AND uf.id.followingId = b.post.author.id
                            )
                        )
                    )
              )
            ORDER BY b.createdAt DESC
            """)
    Page<CheerPostBookmark> findVisibleByUserIdOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("viewerId") Long viewerId,
            Pageable pageable);

    List<CheerPostBookmark> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);

    @Query("SELECT b.post.id AS postId, COUNT(b) AS bookmarkCount " +
            "FROM CheerPostBookmark b " +
            "WHERE b.post.id IN :postIds " +
            "GROUP BY b.post.id")
    List<PostBookmarkCount> countByPostIds(@Param("postIds") List<Long> postIds);

    long countById_PostId(Long postId);
}
