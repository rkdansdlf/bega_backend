package com.example.cheerboard.storage.repository;

import com.example.cheerboard.storage.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    /**
     * 게시글의 모든 이미지 조회
     */
    @Transactional(readOnly = true)
    List<PostImage> findByPostIdOrderByCreatedAtAsc(Long postId);

    /**
     * 여러 게시글의 이미지 일괄 조회
     */
    List<PostImage> findByPostIdInOrderByPostIdAscCreatedAtAsc(List<Long> postIds);

    /**
     * 게시글의 이미지 개수 카운트
     */
    long countByPostId(Long postId);

    /**
     * 게시글의 썸네일 이미지 조회
     */
    Optional<PostImage> findByPostIdAndIsThumbnailTrue(Long postId);

    @Query("SELECT image FROM PostImage image WHERE image.id = :imageId AND image.post.id = :postId")
    Optional<PostImage> findByIdAndPostId(@Param("imageId") Long imageId, @Param("postId") Long postId);

    /**
     * 게시글 삭제 시 모든 이미지 삭제 (CASCADE로 자동 처리됨)
     */
    @Modifying
    @Query("DELETE FROM PostImage image WHERE image.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
