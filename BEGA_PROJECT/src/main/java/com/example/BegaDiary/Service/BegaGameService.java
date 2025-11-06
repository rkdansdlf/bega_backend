package com.example.BegaDiary.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.BegaDiary.Entity.BegaGame;
import com.example.BegaDiary.Entity.GameResponseDto;
import com.example.BegaDiary.Repository.BegaGameRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BegaGameService {
	
	public class GameMapper {
		
		private static final Map<String, String> TEAM_KOREAN_NAME_MAP = Map.ofEntries(
		        Map.entry("HH", "한화 이글스"),
		        Map.entry("HT", "기아 타이거즈"),
		        Map.entry("KT", "KT 위즈"),
		        Map.entry("LG", "LG 트윈스"),
		        Map.entry("LT", "롯데 자이언츠"),
		        Map.entry("NC", "NC 다이노스"),
		        Map.entry("OB", "두산 베어스"),
		        Map.entry("SS", "삼성 라이온즈"),
		        Map.entry("SK", "SSG 랜더스"),
		        Map.entry("WO", "키움 히어로즈")
		    );
		
		private static final Map<String, String> STADIUM_FULL_NAME_MAP = Map.ofEntries(
		        Map.entry("고척", "고척스카이돔"),
		        Map.entry("잠실", "잠실 야구장"),
		        Map.entry("광주", "광주-기아 챔피언스 필드"),
		        Map.entry("대전", "대전 한화생명 이글스 파크"),
		        Map.entry("청주", "청주 야구장"),
		        Map.entry("울산", "울산 문수 야구장"),
		        Map.entry("수원", "수원 kt wiz 파크"),
		        Map.entry("대구", "대구 삼성 라이온즈 파크"),
		        Map.entry("포항", "포항 야구장"),
		        Map.entry("창원", "창원NC파크"),
		        Map.entry("사직", "부산 사직 야구장"),
		        Map.entry("문학", "인천SSG랜더스필드")
		    );
		
		public static String mapStadiumToFullName(String abbreviation) {
	        return STADIUM_FULL_NAME_MAP.getOrDefault(abbreviation, abbreviation);
	    }
		
		public static String mapTeamToKoreanName(String abbreviation) {
	        return TEAM_KOREAN_NAME_MAP.getOrDefault(abbreviation, abbreviation);
	    }
		
	}
	
	
	private final BegaGameRepository gameRepository;
	
	public List<GameResponseDto> getGamesByDate(LocalDate date) {
		List<BegaGame> games = gameRepository.findByGameDate(date);
		return games.stream()
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}
	
	public BegaGame getGameById(Long id) {
		return gameRepository.findById(id).orElse(null);
	}
	
	private GameResponseDto convertToDto(BegaGame game) {
        return GameResponseDto.builder()
            .id(game.getId())
            .homeTeam(game.getHomeTeam())
            .awayTeam(game.getAwayTeam())
            .stadium(game.getStadium())
            .score(game.getScoreString())
            .date(game.getGameDate() != null ? game.getGameDate().toString() : null)
            .build();
    }
	
}
