package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 팀 히스토리 엔티티
 *
 * <p>시즌별 팀 정보를 기록하는 엔티티입니다.
 * 각 시즌마다 팀의 이름, 코드, 로고, 순위 등이 변경될 수 있으며,
 * 이러한 변화를 추적하기 위해 사용됩니다.</p>
 *
 * <p>예시: 2015년 넥센 히어로즈 → 2019년 키움 히어로즈</p>
 */
@Entity
@Table(
    name = "team_history",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"season", "team_code"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // 프랜차이즈 참조 (외래키)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "franchise_id", referencedColumnName = "id", nullable = false)
    private TeamFranchiseEntity franchise;

    // 시즌 연도 (예: 2024)
    @Column(name = "season", nullable = false)
    private Integer season;

    // 해당 시즌의 팀 이름
    @Column(name = "team_name", nullable = false, length = 50)
    private String teamName;

    // 해당 시즌의 팀 코드
    @Column(name = "team_code", nullable = false, length = 10)
    private String teamCode;

    // 로고 URL
    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    // 해당 시즌의 순위 (리그 순위)
    @Column(name = "ranking")
    private Integer ranking;

    // 해당 시즌의 홈 구장
    @Column(name = "stadium", length = 50)
    private String stadium;

    // 해당 시즌의 연고지
    @Column(name = "city", length = 30)
    private String city;

    // 해당 시즌의 팀 색상
    @Column(name = "color", length = 50)
    private String color;

    // 생성일시
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 수정일시
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 프랜차이즈 ID를 반환하는 헬퍼 메서드
     *
     * @return 프랜차이즈 ID (없으면 null)
     */
    public Integer getFranchiseId() {
        return franchise != null ? franchise.getId() : null;
    }

    /**
     * 해당 시즌이 최근 시즌인지 확인하는 헬퍼 메서드
     *
     * @param currentYear 현재 연도
     * @return 최근 시즌이면 true
     */
    public boolean isRecentSeason(int currentYear) {
        return season != null && season >= currentYear - 1;
    }
}
