package com.example.homePage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder 
@NoArgsConstructor
@AllArgsConstructor
public class HomePageGameDto {

	private String gameId;
    private String time;
    private String stadium;
    private String gameStatus; 
    private String gameInfo; // 포스트시즌 정보 등
    private String leagueType; // REGULAR, POSTSEASON, FALL 구분

    // 프론트엔드 GameCard 컴포넌트 구조에 맞춰 팀 정보 매핑
    private String homeTeam; // 팀 약어 ID (LG)
    private String homeTeamFull; // 팀 전체 이름 (LG 트윈스)
    private String awayTeam; // 팀 약어 ID (KT)
    private String awayTeamFull; // 팀 전체 이름 (KT 위즈)
    
    // 스코어 정보 필요한지 확인..
}
