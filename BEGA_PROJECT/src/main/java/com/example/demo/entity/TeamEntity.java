package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * KBO 팀 엔티티
 *
 * <p>특정 시점의 팀 정보를 나타냅니다.
 * 팀은 프랜차이즈에 속하며, 시간이 지나면서 이름이나 속성이 변경될 수 있습니다.</p>
 */
@Entity
@Table(name = "teams", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamEntity {

    // 팀 ID (Primary Key, users.favorite_team과 연결됨)
    @Id
    @Column(name = "team_id", nullable = false, length = 255)
    private String teamId;

    // 팀 이름
    @Column(name = "team_name", nullable = false, length = 255)
    private String teamName;

    // 팀 약어/단축 이름
    @Column(name = "team_short_name", nullable = false, length = 255)
    private String teamShortName;

    // 연고지 도시
    @Column(name = "city", nullable = false, length = 30)
    private String city;

    // 구장 이름
    @Column(name = "stadium_name", length = 50)
    private String stadiumName;

    // 창단 연도
    @Column(name = "founded_year")
    private Integer foundedYear;

    // 팀 대표 색상
    @Column(name = "color")
    private String color;

    // 생성일시
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 수정일시
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 프랜차이즈 참조 (외래키)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "franchise_id", referencedColumnName = "id")
    private TeamFranchiseEntity franchise;

    // 활성 팀 여부 (현재 KBO 리그에 참가 중인지)
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // 팀 별칭 배열 (PostgreSQL text[] 타입)
    @Column(name = "aliases", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] aliases;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 현재 활성화된 KBO 팀인지 확인하는 헬퍼 메서드
     *
     * @return 활성 KBO 팀이면 true
     */
    public boolean isActiveKboTeam() {
        return Boolean.TRUE.equals(isActive);
    }

    /**
     * 프랜차이즈 ID를 반환하는 헬퍼 메서드
     *
     * @return 프랜차이즈 ID (없으면 null)
     */
    public Integer getFranchiseId() {
        return franchise != null ? franchise.getId() : null;
    }
}

