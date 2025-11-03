package com.example.cheerboard.storage.entity;

import com.example.cheerboard.domain.CheerPost;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 게시글 이미지 엔티티
 * - 게시글당 최대 10개 이미지
 * - 게시글 삭제 시 CASCADE로 자동 삭제
 */
@Entity
@Table(name = "post_images")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CheerPost post;

    /**
     * Supabase Storage 경로: posts/{postId}/{uuid}.{ext}
     */
    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "bytes", nullable = false)
    private Long bytes;

    /**
     * 썸네일 여부 (게시글당 1개만 가능)
     */
    @Column(name = "is_thumbnail", nullable = false)
    @Builder.Default
    private Boolean isThumbnail = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
