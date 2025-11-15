package com.example.homePage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomePageGameService {

	private final HomePageGameRepository homePageGameRepository;
    private final HomePageTeamRepository homePageTeamRepository;

    private Map<String, HomePageTeam> teamMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
    	// DB에서 모든 팀 정보를 찾아 Map에 저장
        homePageTeamRepository.findAll().forEach(team -> 
            teamMap.put(team.getTeamId(), team));
    }
    
    // 특정 날짜의 경기 목록을 조회하고 DTO 목록으로 변환
    
    public List<HomePageGameDto> getGamesByDate(LocalDate date) {
    	// 해당 날짜와 일치하는 경기 엔티티(HomePageGame) 목록을 DB에서 조회
        List<HomePageGame> games = homePageGameRepository.findByGameDate(date);
        
        return games.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    private HomePageGameDto convertToDto(HomePageGame homePageGame) {
        // 캐싱된 Map에서 홈팀 및 어웨이팀 정보를 조회
        HomePageTeam homeTeam = teamMap.getOrDefault(homePageGame.getHomeTeamId(), new HomePageTeam());
        HomePageTeam awayTeam = teamMap.getOrDefault(homePageGame.getAwayTeamId(), new HomePageTeam());
        
        // 날짜를 기준으로 리그 타입 결정
        String leagueType = determineLeagueType(homePageGame.getGameDate());
        String gameInfo = "";
        
        // 포스트시즌 특정 경기 정보 설정
        if ("POSTSEASON".equals(leagueType) && homePageGame.getAwayTeamId().equals("삼성") && homePageGame.getHomeTeamId().equals("한화")) {
            gameInfo = "PO 4차전 S-T";
        }
        
        // GameDto.builder()를 사용하여 DTO 객체 생성 및 값 설정
        return HomePageGameDto.builder()
            .gameId(homePageGame.getGameId())
            .stadium(homePageGame.getStadium())
            .gameStatus(homePageGame.getGameStatus())
            
            // 팀 정보
            .homeTeam(homeTeam.getTeamId())
            .homeTeamFull(homeTeam.getTeamName())
            .awayTeam(awayTeam.getTeamId())
            .awayTeamFull(awayTeam.getTeamName())
            
            // 기타 정보
            .time("18:30") // 모든 경기 시간을 18:30으로 임시 고정
            .leagueType(leagueType)
            .gameInfo(gameInfo)
            .build();
    }
    
    // 날짜를 기반으로 해당 날이 어떤 리그 타입에 속하는지 임시로 판단
    private String determineLeagueType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        
        // 1. 비시즌 (Offseason: 11월 1일 이후 ~ 3월 21일 이전)
        // 11월 1일부터 다음 해 3월 21일까지는 비시즌입니다. (올해 개막일 반영)
        if (month >= 11 || month <= 2 || (month == 3 && day < 22)) {
             return "OFFSEASON";
        } 
        
        // 2. 포스트시즌 (Postseason: 10월 6일 ~ 10월 31일)
        // 10월 6일부터 10월 31일까지를 포스트시즌 기간으로 정확하게 잡습니다.
        else if (month == 10 && day >= 6 && day <= 31) {
            return "POSTSEASON";
        }
        
        // 3. 정규시즌 (Regular: 3월 22일 ~ 10월 4일)
        // 3월 22일 개막일부터 정규시즌 종료일인 10월 4일까지를 정규시즌으로 간주합니다.
        else if ((month == 3 && day >= 22) || (month >= 4 && month <= 9) || (month == 10 && day <= 4)) {
            return "REGULAR";
        } 
        
        // 4. 나머지 (10월 5일, 그리고 퓨처스리그 등)
        // 10월 5일은 정규시즌도 아니고 포스트시즌도 아닌 중간 날짜이므로 FALL로 처리됩니다.
        else {
            return "FALL";
        }
    }
 }


