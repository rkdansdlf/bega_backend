package com.example.BegaDiary.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.BegaDiary.Entity.GameResponseDto;
import com.example.BegaDiary.Utils.BaseballConstants;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.util.TeamCodeResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BegaGameService {

	private final GameRepository gameRepository;
	private final GameMetadataRepository gameMetadataRepository;

	public List<GameResponseDto> getGamesByDate(LocalDate date) {
		List<GameEntity> games = gameRepository.findByGameDate(date);
		return Objects.requireNonNull(games.stream()
				.map(this::convertToDto)
				.collect(Collectors.toList()));
	}

	public Long findGameIdByDateAndTeams(String dateStr, String homeTeam, String awayTeam) {
		return findGameIdByDateAndTeams(dateStr, homeTeam, awayTeam, null, null);
	}

	public Long findGameIdByDateAndTeams(
			String dateStr,
			String homeTeam,
			String awayTeam,
			String stadium,
			String time) {
		if (dateStr == null || homeTeam == null || awayTeam == null) {
			log.info(
					"[BegaGameService] ticket_match skipped date={} home={} away={} reason=missing_required_field",
					dateStr, homeTeam, awayTeam);
			return null;
		}

		try {
			LocalDate date = LocalDate.parse(dateStr);
			int seasonYear = date.getYear();
			String homeCanonical = TeamCodeResolver.resolveCanonical(homeTeam, seasonYear);
			String awayCanonical = TeamCodeResolver.resolveCanonical(awayTeam, seasonYear);
			Set<String> homeVariants = TeamCodeResolver.resolveQueryVariants(homeTeam, seasonYear);
			Set<String> awayVariants = TeamCodeResolver.resolveQueryVariants(awayTeam, seasonYear);
			LocalTime ticketTime = parseTicketTime(time);

			log.info(
					"[BegaGameService] team_resolution input_home={} input_away={} resolved_home={} resolved_away={} home_variants={} away_variants={} query_mode={}",
					homeTeam, awayTeam, homeCanonical, awayCanonical, homeVariants, awayVariants,
					TeamCodeResolver.getQueryMode(seasonYear));

			if (homeVariants.isEmpty() || awayVariants.isEmpty()) {
				log.warn(
						"[BegaGameService] ticket_match failed date={} reason=variant_resolution_failed input_home={} input_away={}",
						dateStr, homeTeam, awayTeam);
				return null;
			}

			List<GameEntity> candidates = new ArrayList<>();
			candidates.addAll(gameRepository.findByGameDateAndTeamVariants(
					date,
					new ArrayList<>(homeVariants),
					new ArrayList<>(awayVariants)));
			candidates.addAll(gameRepository.findByGameDateAndTeamVariants(
					date,
					new ArrayList<>(awayVariants),
					new ArrayList<>(homeVariants)));

			Map<String, GameEntity> uniqueByGameId = new HashMap<>();
			for (GameEntity candidate : candidates) {
				uniqueByGameId.putIfAbsent(candidate.getGameId(), candidate);
			}

			if (uniqueByGameId.isEmpty()) {
				log.info(
						"[BegaGameService] ticket_match not_found date={} input_home={} input_away={} rows=0 fallback_used=false",
						dateStr, homeTeam, awayTeam);
				return null;
			}

			Map<String, GameMetadataEntity> metadataByGameId = new HashMap<>();
			List<ScoredGame> scoredGames = uniqueByGameId.values().stream()
					.map(game -> {
						GameMetadataEntity metadata = metadataByGameId.computeIfAbsent(
								game.getGameId(),
								gameId -> gameMetadataRepository.findByGameId(gameId).orElse(null));
						int score = scoreCandidate(
								game,
								homeCanonical,
								awayCanonical,
								game.getGameDate() != null ? game.getGameDate().getYear() : seasonYear,
								stadium,
								ticketTime,
								metadata);
						return new ScoredGame(game, score);
					})
					.filter(scored -> scored.score() > 0)
					.sorted(Comparator
							.comparingInt(ScoredGame::score).reversed()
							.thenComparing(scored -> scored.game().getGameId()))
					.collect(Collectors.toList());

			if (scoredGames.isEmpty()) {
				log.info(
						"[BegaGameService] ticket_match not_found date={} input_home={} input_away={} rows={} fallback_used=false",
						dateStr, homeTeam, awayTeam, uniqueByGameId.size());
				return null;
			}

			ScoredGame best = scoredGames.get(0);
			if (scoredGames.size() > 1 && scoredGames.get(1).score() == best.score()) {
				log.warn(
						"[BegaGameService] ticket_match ambiguous=true date={} top_score={} candidate_game_ids={}",
						dateStr,
						best.score(),
						scoredGames.stream()
								.filter(s -> s.score() == best.score())
								.map(s -> s.game().getGameId())
								.collect(Collectors.toCollection(LinkedHashSet::new)));
				return null;
			}

			log.info(
					"[BegaGameService] ticket_match success=true date={} game_id={} score={} rows={}",
					dateStr, best.game().getGameId(), best.score(), uniqueByGameId.size());
			return Objects.requireNonNull(best.game().getId());
		} catch (Exception e) {
			log.error(
					"[BegaGameService] ticket_match failed date={} input_home={} input_away={} reason={}",
					dateStr, homeTeam, awayTeam, e.getMessage(), e);
			return null;
		}
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

		return Objects.requireNonNull(GameResponseDto.builder()
				.id(game.getId())
				.homeTeam(homeTeamName)
				.awayTeam(awayTeamName)
				.stadium(stadiumName)
				.score(game.getScoreString())
				.date(game.getGameDate() != null ? game.getGameDate().toString() : null)
				.build());
	}

	private int scoreCandidate(
			GameEntity game,
			String homeCanonical,
			String awayCanonical,
			Integer gameYear,
			String stadium,
			LocalTime ticketTime,
			GameMetadataEntity metadata) {
		String dbHomeCanonical = TeamCodeResolver.resolveCanonical(game.getHomeTeam(), gameYear);
		String dbAwayCanonical = TeamCodeResolver.resolveCanonical(game.getAwayTeam(), gameYear);

		int score = 0;
		if (Objects.equals(homeCanonical, dbHomeCanonical) && Objects.equals(awayCanonical, dbAwayCanonical)) {
			score += 100;
		} else if (Objects.equals(homeCanonical, dbAwayCanonical) && Objects.equals(awayCanonical, dbHomeCanonical)) {
			score += 55;
		} else {
			return 0;
		}

		score += scoreStadiumMatch(stadium, game.getStadium(), metadata);
		score += scoreTimeMatch(ticketTime, metadata);
		return score;
	}

	private int scoreStadiumMatch(String ticketStadium, String gameStadium, GameMetadataEntity metadata) {
		if (ticketStadium == null || ticketStadium.isBlank()) {
			return 0;
		}
		String normalizedTicket = normalizeText(ticketStadium);
		if (normalizedTicket.isEmpty()) {
			return 0;
		}

		List<String> candidates = new ArrayList<>();
		if (gameStadium != null) {
			candidates.add(gameStadium);
		}
		if (metadata != null && metadata.getStadiumName() != null) {
			candidates.add(metadata.getStadiumName());
		}
		if (metadata != null && metadata.getStadiumCode() != null) {
			candidates.add(metadata.getStadiumCode());
		}

		for (String candidate : candidates) {
			String normalized = normalizeText(candidate);
			if (!normalized.isEmpty() &&
					(normalized.contains(normalizedTicket) || normalizedTicket.contains(normalized))) {
				return 15;
			}
		}
		return 0;
	}

	private int scoreTimeMatch(LocalTime ticketTime, GameMetadataEntity metadata) {
		if (ticketTime == null || metadata == null || metadata.getStartTime() == null) {
			return 0;
		}
		long minutes = Math.abs(Duration.between(metadata.getStartTime(), ticketTime).toMinutes());
		if (minutes <= 10) {
			return 12;
		}
		if (minutes <= 45) {
			return 8;
		}
		if (minutes <= 90) {
			return 4;
		}
		return 0;
	}

	private LocalTime parseTicketTime(String time) {
		if (time == null || time.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(time.trim());
		} catch (DateTimeParseException ex) {
			log.debug("[BegaGameService] ticket_time_parse_failed input={}", time);
			return null;
		}
	}

	private String normalizeText(String input) {
		if (input == null) {
			return "";
		}
		return input.replaceAll("\\s+", "").toLowerCase();
	}

	private record ScoredGame(GameEntity game, int score) {
	}

}
