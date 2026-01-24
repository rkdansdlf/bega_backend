package com.example.cheerboard.domain;

import com.example.auth.entity.UserEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.cheerboard.storage.entity.PostImage;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "cheer_post")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.annotations.SQLRestriction("deleted = false")
public class CheerPost {

    /**
     * 리포스트 타입: SIMPLE(단순 리포스트), QUOTE(인용 리포스트)
     */
    public enum RepostType {
        SIMPLE,  // 단순 리포스트 - 코멘트 없이 원글 그대로 공유
        QUOTE    // 인용 리포스트 - 원글을 첨부하면서 의견 추가
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamEntity team;

    @Enumerated(EnumType.STRING)
    @Column(name = "posttype", nullable = false, length = 20)
    @Builder.Default
    private PostType postType = PostType.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(nullable = true)  // 리포스트는 제목 없음
    private String title;

    @Column(nullable = true, columnDefinition = "text")  // 단순 리포스트는 내용 없음
    private String content;

    @Column(name = "likecount", nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(name = "commentcount", nullable = false)
    @Builder.Default
    private int commentCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private int views = 0;

    @Column(name = "repostcount", nullable = false)
    @Builder.Default
    private int repostCount = 0;

    /**
     * 원본 게시글 (리포스트인 경우에만 설정)
     * ON DELETE SET NULL: 원본 삭제 시 null로 설정 (인용글은 유지)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repost_of_id")
    private CheerPost repostOf;

    /**
     * 리포스트 타입 (SIMPLE, QUOTE, null=원본 게시글)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "repost_type", length = 10)
    private RepostType repostType;

    @Column(name = "createdat", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updatedat", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // 연관관계 매핑 (cascade 삭제를 위해 추가)
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheerComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheerPostLike> likes = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getTeamId() {
        return team != null ? team.getTeamId() : null;
    }

    /**
     * 리포스트인지 확인
     */
    public boolean isRepost() {
        return repostType != null;
    }

    /**
     * 단순 리포스트인지 확인
     */
    public boolean isSimpleRepost() {
        return repostType == RepostType.SIMPLE;
    }

    /**
     * 인용 리포스트인지 확인
     */
    public boolean isQuoteRepost() {
        return repostType == RepostType.QUOTE;
    }
}
