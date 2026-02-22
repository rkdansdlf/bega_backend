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
@Table(name = "cheer_post", indexes = {
        @Index(name = "idx_cheer_post_type_created_at", columnList = "posttype, createdat DESC"),
        @Index(name = "idx_cheer_team_post_type_created_at", columnList = "team_id, posttype, createdat DESC")
})
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
        SIMPLE, // 단순 리포스트 - 코멘트 없이 원글 그대로 공유
        QUOTE // 인용 리포스트 - 원글을 첨부하면서 의견 추가
    }

    /**
     * 공유 정책 타입
     * INTERNAL_*: 동일 서비스 내부 동작
     * EXTERNAL_*: 외부 콘텐츠 참조/재가공
     */
    public enum ShareMode {
        INTERNAL_REPOST,
        INTERNAL_QUOTE,
        EXTERNAL_LINK,
        EXTERNAL_COPY,
        EXTERNAL_EMBED,
        EXTERNAL_SUMMARY
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

    // title removed

    @Column(columnDefinition = "TEXT", nullable = true) // 단순 리포스트는 내용 없음
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

    @Enumerated(EnumType.STRING)
    @Column(name = "share_mode", length = 24)
    @Builder.Default
    private ShareMode shareMode = ShareMode.INTERNAL_REPOST;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "source_title", length = 300)
    private String sourceTitle;

    @Column(name = "source_author", length = 200)
    private String sourceAuthor;

    @Column(name = "source_license", length = 120)
    private String sourceLicense;

    @Column(name = "source_license_url", length = 1024)
    private String sourceLicenseUrl;

    @Column(name = "source_changed_note", length = 1200)
    private String sourceChangedNote;

    @Column(name = "source_snapshot_type", length = 80)
    private String sourceSnapshotType;

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
        normalizeContent();
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        normalizeContent();
        updatedAt = Instant.now();
    }

    private void normalizeContent() {
        if (content == null) {
            content = "";
        }
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
