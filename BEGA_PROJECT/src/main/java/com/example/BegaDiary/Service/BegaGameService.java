package com.example.BegaDiary.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.BegaDiary.Entity.GameResponseDto;
import com.example.BegaDiary.Utils.BaseballConstants;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BegaGameService {

	private final GameRepository gameRepository;

	public List<GameResponseDto> getGamesByDate(LocalDate date) {
		List<GameEntity> games = gameRepository.findByGameDate(date);
		return games.stream()
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}

	public Long findGameIdByDateAndTeams(String dateStr, String homeTeam, String awayTeam) {
		if (dateStr == null || homeTeam == null || awayTeam == null) {
			return null;
		}

		try {
			LocalDate date = LocalDate.parse(dateStr);
			List<GameEntity> games = gameRepository.findByGameDate(date);

			for (GameEntity game : games) {
				String dbHome = game.getHomeTeam();
				String dbAway = game.getAwayTeam();

				// OCR 결과와 DB 데이터 단순 비교 (포함 관계 확인)
				if ((homeTeam.contains(dbHome) || dbHome.contains(homeTeam)) &&
						(awayTeam.contains(dbAway) || dbAway.contains(awayTeam))) {
					return game.getId();
				}
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public GameEntity getGameById(Long id) {
		if (id == null) {
			return null;
		}
		return gameRepository.findById(Objects.requireNonNull(id)).orElse(null);
	}

	private GameResponseDto convertToDto(GameEntity game) {

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