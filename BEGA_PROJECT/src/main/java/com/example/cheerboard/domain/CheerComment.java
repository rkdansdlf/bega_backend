package com.example.cheerboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity 
@Table(name = "cheer_comment")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CheerComment {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) 
    @JoinColumn(name = "post_id", nullable = false)
    private CheerPost post;

    @ManyToOne(fetch = FetchType.LAZY) 
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { 
        createdAt = updatedAt = Instant.now(); 
    }

    @PreUpdate
    void onUpdate() { 
        updatedAt = Instant.now(); 
    }
}