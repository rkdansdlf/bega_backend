package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Supabase의 public 스키마에 있는 팀 정보를 매핑하는 엔티티입니다.
 * SQL 정의에 따라 모든 컬럼을 포함합니다.
 */
@Entity
@Table(name = "teams", schema = "public") // public 스키마 지정
@Getter
@Setter
@NoArgsConstructor // Lombok의 기본 생성자
public class TeamEntity {

    /**
     * 팀 ID (Primary Key, users.favorite_team과 연결됨)
     * character varying(10) not null
     */
    @Id
    @Column(name = "team_id", nullable = false, length = 10)
    private String teamId;

    /**
     * 팀 이름
     * character varying(50) not null
     */
    @Column(name = "team_name", nullable = false, length = 50)
    private String teamName;

    /**
     * 팀 약어/단축 이름
     * character varying(20) not null
     */
    @Column(name = "team_short_name", nullable = false, length = 20)
    private String teamShortName;

    /**
     * 연고지 도시
     * character varying(30) not null
     */
    @Column(name = "city", nullable = false, length = 30)
    private String city;

    /**
     * 창단 연도
     * integer null
     */
    @Column(name = "founded_year")
    private Integer foundedYear;

    /**
     * 구장 이름
     * character varying(50) null
     */
    @Column(name = "stadium_name", length = 50)
    private String stadiumName;

    /**
     * 팀 대표 색상
     * character varying null
     */
    @Column(name = "color")
    private String color;
}
