package com.example.prediction;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.config.CacheConfig;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.util.KboTeamCodePolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingPredictionService {

	private static final int RANKING_TEAM_COUNT = 10;
	private static final String RANKING_PREDICTION_CLOSED_CODE = "RANKING_PREDICTION_CLOSED";
	private static final String RANKING_PREDICTION_ALREADY_EXISTS_CODE = "RANKING_PREDICTION_ALREADY_EXISTS";
	private static final String RANKING_PREDICTION_NOT_FOUND_CODE = "RANKING_PREDICTION_NOT_FOUND";
	private static final String INVALID_RANKING_PREDICTION_CODE = "INVALID_RANKING_PREDICTION";
	private static final String RANKING_PREDICTION_CLOSED_MESSAGE = "현재는 순위 예측 기간이 아닙니다. (예측 가능 기간: 11월 1일 ~ 5월 31일)";
	private static final String DUPLICATE_CONSTRAINT_NAME = "uk_rank_pred_user_season";
	private static final Set<String> CANONICAL_TEAM_CODES = KboTeamCodePolicy.CANONICAL_CODES;

	private final RankingPredictionRepository rankingPredictionRepository;
	private final GameRepository gameRepository;
	private final com.example.homepage.HomePageTeamRepository homePageTeamRepository;
	private final UserRepository userRepository;
	private final CacheManager cacheManager;

	// 순위 예측을 저장 (수정 불가, 1회만 가능)
	@Transactional(transactionManager = "transactionManager")
	public RankingPredictionResponseDto savePrediction(
			RankingPredictionRequestDto requestDto,
			String userIdString) {
		ensurePredictionPeriodOpen();

		int currentSeasonYear = SeasonUtils.getCurrentPredictionSeason();
		List<String> normalizedPredictionData = normalizePredictionData(requestDto, currentSeasonYear);

		boolean alreadyPredicted = rankingPredictionRepository
				.existsByUserIdAndSeasonYear(userIdString, currentSeasonYear);

		if (alreadyPredicted) {
			throw new ConflictBusinessException(
					RANKING_PREDICTION_ALREADY_EXISTS_CODE,
					"이미 " + currentSeasonYear + " 시즌 순위 예측을 완료하셨습니다.");
		}

		RankingPrediction newPrediction = new RankingPrediction(
				userIdString,
				currentSeasonYear,
				normalizedPredictionData);

		RankingPrediction saved;
		try {
			saved = rankingPredictionRepository.saveAndFlush(newPrediction);
		} catch (DataIntegrityViolationException ex) {
			if (isDuplicatePredictionConstraintViolation(ex)) {
				throw new ConflictBusinessException(
						RANKING_PREDICTION_ALREADY_EXISTS_CODE,
						"이미 " + currentSeasonYear + " 시즌 순위 예측을 완료하셨습니다.");
			}
			throw ex;
		}
		return Objects.requireNonNull(convertToResponseDto(saved));
	}

	@Transactional(readOnly = true, transactionManager = "transactionManager")
	public RankingPredictionResponseDto getPrediction(String userIdString, int seasonYear) {
		return rankingPredictionRepository.findByUserIdAndSeasonYear(userIdString, seasonYear)
				.map(this::convertToResponseDto)
				.orElseThrow(() -> new NotFoundBusinessException(
						RANKING_PREDICTION_NOT_FOUND_CODE,
						"저장된 시즌 순위 예측을 찾을 수 없습니다."));
	}

	@Transactional(readOnly = true, transactionManager = "transactionManager")
	public RankingPredictionResponseDto getPredictionByShareIdAndSeason(String shareId, int seasonYear) {
		UserEntity user = findUserByShareId(shareId);
		if (user == null) {
			return null;
		}
		return rankingPredictionRepository.findByUserIdAndSeasonYear(String.valueOf(user.getId()), seasonYear)
				.map(prediction -> convertToResponseDto(
						prediction,
						user.getUniqueId() == null ? null : user.getUniqueId().toString()))
				.orElse(null);
	}

	private RankingPredictionResponseDto convertToResponseDto(RankingPrediction prediction) {
		return convertToResponseDto(prediction, resolveShareId(prediction.getUserId()));
	}

	private RankingPredictionResponseDto convertToResponseDto(RankingPrediction prediction, String resolvedShareId) {
		List<RankingPredictionResponseDto.TeamRankingDetail> details = new ArrayList<>();
		RankingPredictionContextSnapshot context = getRankingContext(prediction.getSeasonYear());
		Map<String, Integer> currentRankMap = context.getCurrentRankMap();
		Map<String, Integer> lastRankMap = context.getLastRankMap();
		Map<String, String> teamNameMap = context.getTeamNameMap();
		List<String> predictionData = prediction.getPredictionData() == null ? List.of() : List.copyOf(prediction.getPredictionData());

		for (String teamId : predictionData) {
			details.add(new RankingPredictionResponseDto.TeamRankingDetail(
					teamId,
					teamNameMap.getOrDefault(teamId, teamId),
					currentRankMap.get(teamId),
					lastRankMap.get(teamId)));
		}

		return Objects.requireNonNull(new RankingPredictionResponseDto(
				prediction.getId(),
				resolvedShareId,
				prediction.getSeasonYear(),
				predictionData,
				details,
				prediction.getCreatedAt()));
	}

	private RankingPredictionContextSnapshot getRankingContext(int seasonYear) {
		Cache cache = cacheManager.getCache(CacheConfig.RANKING_PREDICTION_CONTEXT);
		String cacheKey = String.valueOf(seasonYear);
		if (cache != null) {
			Cache.ValueWrapper wrapper = cache.get(cacheKey);
			Object value = wrapper == null ? null : wrapper.get();
			if (value instanceof RankingPredictionContextSnapshot snapshot) {
				return snapshot;
			}
		}

		Map<String, Integer> currentRankMap = extractRankMap(gameRepository.findTeamRankingsBySeason(seasonYear));
		Map<String, Integer> lastRankMap = extractRankMap(gameRepository.findTeamRankingsBySeason(seasonYear - 1));
		Map<String, String> teamNameMap = new HashMap<>();
		homePageTeamRepository.findAll().forEach(t -> teamNameMap.put(t.getTeamId(), t.getTeamName()));

		RankingPredictionContextSnapshot snapshot = new RankingPredictionContextSnapshot(
				currentRankMap,
				lastRankMap,
				teamNameMap);
		if (cache != null) {
			cache.put(cacheKey, snapshot);
		}
		return snapshot;
	}

	private Map<String, Integer> extractRankMap(java.util.List<Object[]> rankings) {
		Map<String, Integer> rankMap = new HashMap<>();
		for (Object[] row : rankings) {
			if (row[0] != null && row[1] != null) {
				rankMap.put(String.valueOf(row[1]), ((Number) row[0]).intValue());
			}
		}
		return rankMap;
	}

	public int getCurrentSeason() {
		ensurePredictionPeriodOpen();
		return SeasonUtils.getCurrentPredictionSeason();
	}

	private UserEntity findUserByShareId(String shareId) {
		if (shareId == null || shareId.isBlank()) {
			throw new IllegalArgumentException("공유 식별자가 필요합니다.");
		}

		final UUID uniqueId;
		try {
			uniqueId = UUID.fromString(shareId.trim());
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("공유 식별자 형식이 올바르지 않습니다.");
		}

		return userRepository.findByUniqueId(uniqueId).orElse(null);
	}

	private String resolveShareId(String userIdString) {
		if (userIdString == null || userIdString.isBlank()) {
			return null;
		}

		Cache cache = cacheManager.getCache(CacheConfig.RANKING_SHARE_IDS);
		if (cache != null) {
			Cache.ValueWrapper wrapper = cache.get(userIdString);
			Object value = wrapper == null ? null : wrapper.get();
			if (value instanceof String cachedShareId) {
				return cachedShareId;
			}
		}

		final Long userId;
		try {
			userId = Long.valueOf(userIdString);
		} catch (NumberFormatException ex) {
			return null;
		}

		String shareId = userRepository.findById(userId)
				.map(UserEntity::getUniqueId)
				.map(UUID::toString)
				.orElse(null);
		if (cache != null && shareId != null) {
			cache.put(userIdString, shareId);
		}
		return shareId;
	}

	private void ensurePredictionPeriodOpen() {
		if (!SeasonUtils.isPredictionPeriod()) {
			throw new ConflictBusinessException(
					RANKING_PREDICTION_CLOSED_CODE,
					RANKING_PREDICTION_CLOSED_MESSAGE);
		}
	}

	private List<String> normalizePredictionData(RankingPredictionRequestDto requestDto, int currentSeasonYear) {
		if (requestDto == null) {
			throw new BadRequestBusinessException(
					INVALID_RANKING_PREDICTION_CODE,
					"순위 예측 요청이 올바르지 않습니다.");
		}

		Integer seasonYear = requestDto.getSeasonYear();
		if (seasonYear == null || seasonYear != currentSeasonYear) {
			throw new BadRequestBusinessException(
					INVALID_RANKING_PREDICTION_CODE,
					"현재는 " + currentSeasonYear + " 시즌만 예측 가능합니다.");
		}

		List<String> teamIdsInOrder = requestDto.getTeamIdsInOrder();
		if (teamIdsInOrder == null || teamIdsInOrder.size() != RANKING_TEAM_COUNT) {
			throw new BadRequestBusinessException(
					INVALID_RANKING_PREDICTION_CODE,
					"중복 없이 " + RANKING_TEAM_COUNT + "개 팀을 모두 선택해야 합니다.");
		}

		List<String> normalized = new ArrayList<>(teamIdsInOrder.size());
		Set<String> uniqueTeamCodes = new HashSet<>();
		for (String teamId : teamIdsInOrder) {
			String canonicalTeamId = normalizeCanonicalTeamId(teamId);
			if (canonicalTeamId == null || !CANONICAL_TEAM_CODES.contains(canonicalTeamId)) {
				throw new BadRequestBusinessException(
						INVALID_RANKING_PREDICTION_CODE,
						"지원하지 않는 팀 코드가 포함되어 있습니다.");
			}
			if (!uniqueTeamCodes.add(canonicalTeamId)) {
				throw new BadRequestBusinessException(
						INVALID_RANKING_PREDICTION_CODE,
						"중복 없이 " + RANKING_TEAM_COUNT + "개 팀을 모두 선택해야 합니다.");
			}
			normalized.add(canonicalTeamId);
		}

		if (normalized.size() != RANKING_TEAM_COUNT || uniqueTeamCodes.size() != RANKING_TEAM_COUNT) {
			throw new BadRequestBusinessException(
					INVALID_RANKING_PREDICTION_CODE,
					"중복 없이 " + RANKING_TEAM_COUNT + "개 팀을 모두 선택해야 합니다.");
		}

		return List.copyOf(normalized);
	}

	private String normalizeCanonicalTeamId(String teamId) {
		if (teamId == null) {
			return null;
		}

		String normalized = teamId.trim().toUpperCase(Locale.ROOT);
		return normalized.isBlank() ? null : normalized;
	}

	private boolean isDuplicatePredictionConstraintViolation(DataIntegrityViolationException ex) {
		String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
		if (message == null) {
			return false;
		}

		String normalizedMessage = message.toLowerCase(Locale.ROOT);
		return normalizedMessage.contains(DUPLICATE_CONSTRAINT_NAME)
				|| normalizedMessage.contains("ranking_predictions")
						&& normalizedMessage.contains("user_id")
						&& normalizedMessage.contains("season_year");
	}

}
