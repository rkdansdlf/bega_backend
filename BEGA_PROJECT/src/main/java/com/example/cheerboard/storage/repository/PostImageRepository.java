package com.example.cheerboard.storage.repository;

import com.example.cheerboard.storage.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    /**
     * 게시글의 모든 이미지 조회
     */
    /**
     * Native on purpose: a derived query joins the post, and the post's soft-delete restriction would hide the
     * images of a soft-deleted post from the purge that has to remove their storage objects.
     */
    @Transactional(readOnly = true)
    @Query(value = "SELECT * FROM post_images WHERE post_id = :postId ORDER BY created_at, id", nativeQuery = true)
    List<PostImage> findByPostIdOrderByCreatedAtAsc(@Param("postId") Long postId);

    /**
     * 여러 게시글의 이미지 일괄 조회
     */
    List<PostImage> findByPostIdInOrderByPostIdAscCreatedAtAsc(List<Long> postIds);

    /**
     * 게시글의 이미지 개수 카운트. Native so a soft-deleted post's images are still counted, as the purge needs.
     */
    @Query(value = "SELECT COUNT(*) FROM post_images WHERE post_id = :postId", nativeQuery = true)
    long countByPostId(@Param("postId") Long postId);

    /**
     * 게시글의 썸네일 이미지 조회
     */
    Optional<PostImage> findByPostIdAndIsThumbnailTrue(Long postId);

    @Query("SELECT image FROM PostImage image WHERE image.id = :imageId AND image.post.id = :postId")
    Optional<PostImage> findByIdAndPostId(@Param("imageId") Long imageId, @Param("postId") Long postId);

    @Query(value = "SELECT COUNT(*) FROM post_images WHERE post_id <> :postId AND storage_path IN (:paths)", nativeQuery = true)
    long countOtherPostReferences(@Param("postId") Long postId, @Param("paths") Collection<String> paths);

    @Query(value = "SELECT COUNT(*) FROM post_images WHERE id <> :imageId AND storage_path = :path", nativeQuery = true)
    long countOtherImageReferences(@Param("imageId") Long imageId, @Param("path") String path);

    /**
     * 게시글 삭제 시 모든 이미지 삭제 (CASCADE로 자동 처리됨)
     */
    /**
     * Native on purpose: the JPQL form joins the post, so the post's soft-delete restriction makes it match no rows
     * for a soft-deleted post, and the purge's foreign key then blocks the post's hard delete.
     */
    @Modifying
    @Query(value = "DELETE FROM post_images WHERE post_id = :postId", nativeQuery = true)
    void deleteByPostId(@Param("postId") Long postId);
}
