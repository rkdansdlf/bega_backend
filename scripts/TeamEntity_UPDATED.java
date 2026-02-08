package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * KBO 팀 엔티티
 *
 * <p>Database Schema:</p>
 * <ul>
 *   <li>teams: 현재 10개 KBO 구단 + 과거 구단 + 국제대회 팀 (총 44개)</li>
 *   <li>franchise_id: team_franchises 테이블과 FK 관계</li>
 *   <li>is_active: 현재 활동 중인 10개 구단만 true</li>
 *   <li>aliases: 과거 팀 코드 매핑 (예: OB → [두산, DO])</li>
 * </ul>
 *
 * @see TeamFranchiseEntity
 * @see TeamHistoryEntity
 */
@Entity
@Table(name = "teams", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class TeamEntity {

    /**
     * 팀 ID (Primary Key)
     * <p>예: SS, LOT, LG, OB, KIA, WO, HH, SSG, NC, KT</p>
     * <p>Foreign Key: users.favorite_team, predictions.team_code 등</p>
     */
    @Id
    @Column(name = "team_id", nullable = false, length = 10)
    private String teamId;

    /**
     * 팀 정식 명칭
     * <p>예: 삼성 라이온즈, 두산 베어스</p>
     */
    @Column(name = "team_name", nullable = false, length = 50)
    private String teamName;

    /**
     * 팀 약어/단축 이름
     * <p>예: 삼성, 두산</p>
     */
    @Column(name = "team_short_name", nullable = false, length = 20)
    private String teamShortName;

    /**
     * 연고지 도시
     * <p>예: 대구, 서울</p>
     */
    @Column(name = "city", nullable = false, length = 30)
    private String city;

    /**
     * 창단 연도
     * <p>예: 1982, 2013</p>
     */
    @Column(name = "founded_year")
    private Integer foundedYear;

    /**
     * 구장 이름
     * <p>예: 대구삼성라이온즈파크, 잠실야구장</p>
     */
    @Column(name = "stadium_name", length = 50)
    private String stadiumName;

    /**
     * 팀 대표 색상 (Hex code)
     * <p>예: #074CA1 (삼성 블루)</p>
     */
    @Column(name = "color")
    private String color;

    /**
     * 프랜차이즈 ID (Foreign Key)
     * <p>team_franchises.franchise_id 참조</p>
     * <p>NULL: 국제대회 팀 또는 특수 팀</p>
     */
    @Column(name = "franchise_id")
    private Integer franchiseId;

    /**
     * 활성 상태 (현재 운영 중인 구단 여부)
     * <p>true: 현재 10개 KBO 구단</p>
     * <p>false: 과거 구단 (해태, OB, MBC 등) + 국제대회 팀</p>
     *
     * <p>Usage:</p>
     * <pre>
     * // API에서 현재 구단만 조회
     * teamRepository.findByIsActiveTrue()
     * </pre>
     */
    @Column(name = "is_active")
    private Boolean isActive;

    /**
     * 별칭/과거 코드 배열
     * <p>PostgreSQL text[] 타입</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>OB → [두산, DO]</li>
     *   <li>KIA → [해태, HT]</li>
     *   <li>SSG → [SK, SL, SK 와이번스]</li>
     * </ul>
     *
     * <p>Usage:</p>
     * <pre>
     * // 별칭으로 팀 검색
     * teamRepository.findByTeamIdOrAlias("두산") // OB 팀 반환
     * </pre>
     *
     * <p><strong>Important:</strong> PostgreSQL text[] 배열 처리 방법 3가지:</p>
     * <ol>
     *   <li><strong>Option A:</strong> hibernate-types 라이브러리 사용 (권장)
     *     <pre>
     *     // build.gradle
     *     implementation 'io.hypersistence:hypersistence-utils-hibernate-63:3.7.0'
     *
     *     // Entity
     *     {@literal @}Type(JsonBinaryType.class)
     *     {@literal @}Column(name = "aliases", columnDefinition = "text[]")
     *     private String[] aliases;
     *     </pre>
     *   </li>
     *   <li><strong>Option B:</strong> JPA AttributeConverter 사용
     *     <pre>
     *     {@literal @}Convert(converter = StringArrayConverter.class)
     *     {@literal @}Column(name = "aliases", columnDefinition = "text[]")
     *     private String[] aliases;
     *
     *     // StringArrayConverter 클래스 구현 필요
     *     </pre>
     *   </li>
     *   <li><strong>Option C:</strong> 문자열로 저장, 서비스 레이어에서 파싱 (간단)
     *     <pre>
     *     {@literal @}Column(name = "aliases")
     *     private String aliases; // 쉼표로 구분: "두산,DO"
     *
     *     // Service에서 split(",") 처리
     *     </pre>
     *   </li>
     * </ol>
     */
    @Column(name = "aliases", columnDefinition = "text[]")
    private String[] aliases;

    // NOTE: PostgreSQL array 처리를 위해 위 3가지 옵션 중 하나 선택
    // 현재는 Option A (hibernate-types) 주석 처리 상태
    // 프로젝트에 맞게 수정하여 사용

    /**
     * ManyToOne 관계 (선택사항)
     * <p>franchise_id 대신 직접 TeamFranchiseEntity 참조</p>
     *
     * <p>활성화 시:</p>
     * <pre>
     * {@literal @}ManyToOne(fetch = FetchType.LAZY)
     * {@literal @}JoinColumn(name = "franchise_id", insertable = false, updatable = false)
     * private TeamFranchiseEntity franchise;
     * </pre>
     */
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "franchise_id", insertable = false, updatable = false)
    // private TeamFranchiseEntity franchise;

    // =========================
    // Helper Methods
    // =========================

    /**
     * 현재 운영 중인 KBO 구단인지 확인
     */
    public boolean isCurrentKboTeam() {
        return Boolean.TRUE.equals(this.isActive) && this.franchiseId != null;
    }

    /**
     * 과거 구단인지 확인
     */
    public boolean isHistoricalTeam() {
        return Boolean.FALSE.equals(this.isActive) && this.franchiseId != null;
    }

    /**
     * 국제대회/특수 팀인지 확인
     */
    public boolean isInternationalTeam() {
        return this.franchiseId == null;
    }

    /**
     * 별칭 포함 검색 (배열이 String[]인 경우)
     */
    public boolean hasAlias(String alias) {
        if (this.aliases == null) {
            return false;
        }
        for (String a : this.aliases) {
            if (a.equals(alias)) {
                return true;
            }
        }
        return false;
    }
}
