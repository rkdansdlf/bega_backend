package com.example.cheerboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@Entity 
@Table(name = "cheer_post")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CheerPost {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String teamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostType postType = PostType.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY) 
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int likeCount;

    @Column(nullable = false)
    private int commentCount;

    @Column(nullable = false)
    private int views;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "cheer_post_images", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();
    
    // 연관관계 매핑 (cascade 삭제를 위해 추가)
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheerComment> comments = new ArrayList<>();
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheerPostLike> likes = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
        likeCount = 0; 
        commentCount = 0;
        views = 0;
    }

    @PreUpdate
    void onUpdate() { 
        updatedAt = Instant.now(); 
    }
}