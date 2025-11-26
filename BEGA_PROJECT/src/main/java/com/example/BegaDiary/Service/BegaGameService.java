package com.example.BegaDiary.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.BegaDiary.Entity.BegaGame;
import com.example.BegaDiary.Entity.GameResponseDto;
import com.example.BegaDiary.Repository.BegaGameRepository;
import com.example.BegaDiary.Utils.BaseballConstants;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BegaGameService {
	
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
		
		String homeTeamName = BaseballConstants.getTeamKoreanName(game.getHomeTeam());
        String awayTeamName = BaseballConstants.getTeamKoreanName(game.getAwayTeam());
        String stadiumName = BaseballConstants.getFullStadiumName(game.getStadium());
		
        return GameResponseDto.builder()
            .id(game.getId())
            .homeTeam(homeTeamName)
            .awayTeam(awayTeamName)
            .stadium(stadiumName)
            .score(game.getScoreString())
            .date(game.getGameDate() != null ? game.getGameDate().toString() : null)
            .build();
    }
	
}
