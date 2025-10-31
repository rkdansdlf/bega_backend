package com.example.cheerboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Setter;

/**
 * KBO 구단 정보를 나타내는 엔티티.
 *
 * <p>현재 게시판에서는 팀 식별자만 필요하므로 기본 키만 매핑합니다.
 * 추가 컬럼이 필요해지면 여기에서 확장할 수 있습니다.</p>
 */
@Entity
@Table(name = "teams", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @Column(name = "team_id", length = 10, nullable = false)
    private String id;

    @Column(name = "team_name", nullable = false, length = 50)
    private String name;

    @Column(name = "team_short_name", nullable = false, length = 20)
    private String shortName;

    @Column(nullable = false, length = 30)
    private String city;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "stadium_name", length = 50)
    private String stadiumName;

    @Column
    private String color;
}
