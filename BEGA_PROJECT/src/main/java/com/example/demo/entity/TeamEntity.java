package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "teams", schema = "public") 
@Getter
@Setter
@NoArgsConstructor 
public class TeamEntity {

    // 팀 ID (Primary Key, users.favorite_team과 연결됨)
    @Id
    @Column(name = "team_id", nullable = false, length = 10)
    private String teamId;

     // 팀 이름
    @Column(name = "team_name", nullable = false, length = 50)
    private String teamName;

    // 팀 약어/단축 이름
    @Column(name = "team_short_name", nullable = false, length = 20)
    private String teamShortName;

     // 연고지 도시
    @Column(name = "city", nullable = false, length = 30)
    private String city;

    // 창단 연도
    @Column(name = "founded_year")
    private Integer foundedYear;

    // 구장 이름
    @Column(name = "stadium_name", length = 50)
    private String stadiumName;

    // 팀 대표 색상
    @Column(name = "color")
    private String color;
}
