package com.example.BegaDiary.Entity;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiaryStatisticsDto {
 private Integer totalCount;           
 private Integer totalWins;            
 private Integer totalLosses;          
 private Integer totalDraws;           
 private Double winRate;               
 
 private Integer monthlyCount;
 
 // 연간 통계
 private Integer yearlyCount;          
 private Integer yearlyWins;           
 private Double yearlyWinRate;         
 
 // 구장/달 통계
 private String mostVisitedStadium;    
 private Integer mostVisitedCount;     
 private String happiestMonth;         
 private Integer happiestCount;        
 private String firstDiaryDate;    
 
 private Integer cheerPostCount;
 private Integer mateParticipationCount;

 private Map<String, Long> emojiCounts;
}
