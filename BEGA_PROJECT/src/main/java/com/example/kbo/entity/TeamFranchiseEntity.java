package com.example.kbo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 프랜차이즈(구단) 엔티티
 *
 * <p>KBO 구단의 역사를 관리하는 엔티티입니다.
 * 팀 명칭이 변경되어도 프랜차이즈는 동일하게 유지됩니다.
 * 예: 히어로즈(2008~2014) → 넥센히어로즈(2015~2018) → 키움히어로즈(2019~현재)</p>
 */
@Entity
@Table(name = "team_franchises", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamFranchiseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // 프랜차이즈 이름 (예: "키움 히어로즈")
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // 원래 팀 코드 (역사적 팀 코드, 예: "HH")
    @Column(name = "original_code", nullable = false, unique = true, length = 10)
    private String originalCode;

    // 현재 팀 코드 (최신 팀 코드, 예: "WO")
    @Column(name = "current_code", nullable = false, length = 10)
    private String currentCode;

    // 생성일시
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 수정일시
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 메타데이터 (JSON 형태, 추가 정보 저장용)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    // 공식 웹사이트 URL
    @Column(name = "web_url", length = 255)
    private String webUrl;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
