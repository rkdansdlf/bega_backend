package com.example.homePage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


 // 리그 시작 날짜 정보를 클라이언트에 전달하기 위한 DTO , 
 // 각 리그(정규시즌, 포스트시즌, 한국시리즈)의 시작 날짜 제공
 
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueStartDatesDto {
    
    //정규시즌 시작 날짜 예: "2025-03-22"
    private String regularSeasonStart;
    
    // 포스트시즌 시작 날짜 예: "2025-10-06" 
    private String postseasonStart;
    
    
    // 한국시리즈 시작 날짜  예: "2025-10-26"
    private String koreanSeriesStart;
}

