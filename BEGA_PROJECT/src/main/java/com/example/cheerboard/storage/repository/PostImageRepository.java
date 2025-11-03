package com.example.cheerboard.storage.repository;

import com.example.cheerboard.storage.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    /**
     * 게시글의 모든 이미지 조회
     */
    List<PostImage> findByPostIdOrderByCreatedAtAsc(Long postId);

    /**
     * 게시글의 이미지 개수 카운트
     */
    long countByPostId(Long postId);

    /**
     * 게시글의 썸네일 이미지 조회
     */
    Optional<PostImage> findByPostIdAndIsThumbnailTrue(Long postId);

    /**
     * 게시글 삭제 시 모든 이미지 삭제 (CASCADE로 자동 처리됨)
     */
    void deleteByPostId(Long postId);
}
