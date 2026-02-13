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

    List<CheerPostBookmark> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);

    @Query("SELECT b.post.id AS postId, COUNT(b) AS bookmarkCount " +
            "FROM CheerPostBookmark b " +
            "WHERE b.post.id IN :postIds " +
            "GROUP BY b.post.id")
    List<PostBookmarkCount> countByPostIds(@Param("postIds") List<Long> postIds);

    long countById_PostId(Long postId);
}
