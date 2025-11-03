package com.example.cheerboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Entity 
@Table(name = "cheer_post_like")
@Getter @Setter @NoArgsConstructor
public class CheerPostLike {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY) 
    @MapsId("postId")
    @JoinColumn(name = "post_id")
    private CheerPost post;

    @ManyToOne(fetch = FetchType.LAZY) 
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist 
    void onCreate() { 
        createdAt = Instant.now(); 
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Embeddable
    @EqualsAndHashCode
    public static class Id implements Serializable {
        private Long postId;
        private Long userId;
    }
}