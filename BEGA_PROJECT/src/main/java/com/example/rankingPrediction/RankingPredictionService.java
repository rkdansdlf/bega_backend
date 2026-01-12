package com.example.rankingPrediction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.repo.GameRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingPredictionService {

	private final RankingPredictionRepository rankingPredictionRepository;
	private final GameRepository gameRepository;
	private final com.example.homePage.HomePageTeamRepository homePageTeamRepository;

	// 순위 예측을 저장 (수정 불가, 1회만 가능)
	@Transactional
	public RankingPredictionResponseDto savePrediction(
			RankingPredictionRequestDto requestDto, 
			String userIdString) {

		// ... (이전 코드 유지)
		if (!SeasonUtils.isPredictionPeriod()) {
			throw new IllegalStateException("현재는 순위 예측 기간이 아닙니다.");
		}

		int currentSeasonYear = SeasonUtils.getCurrentPredictionSeason();
		if (requestDto.getSeasonYear() != currentSeasonYear) {
			throw new IllegalStateException("현재는 " + currentSeasonYear + " 시즌만 예측 가능합니다.");
		}
		
		Long currentUserId = Long.valueOf(userIdString);
		boolean alreadyPredicted = rankingPredictionRepository
				.existsByUserIdAndSeasonYear(currentUserId, currentSeasonYear);

		if (alreadyPredicted) {
			throw new IllegalStateException("이미 " + currentSeasonYear + " 시즌 순위 예측을 완료하셨습니다.");
		}

		RankingPrediction newPrediction = new RankingPrediction(
				currentUserId,
				currentSeasonYear,
				requestDto.getTeamIdsInOrder()
		);

		RankingPrediction saved = rankingPredictionRepository.save(newPrediction);
		return convertToResponseDto(saved);
	}

	@Transactional(readOnly = true)
	public RankingPredictionResponseDto getPrediction(String userIdString, int seasonYear) {
		Long userId = Long.valueOf(userIdString);
		return rankingPredictionRepository.findByUserIdAndSeasonYear(userId, seasonYear)
				.map(this::convertToResponseDto)
				.orElse(null);
	}
	
	@Transactional(readOnly = true)
	public RankingPredictionResponseDto getPredictionByUserIdAndSeason(Long userId, int seasonYear) {
	    return rankingPredictionRepository.findByUserIdAndSeasonYear(userId, seasonYear)
	            .map(this::convertToResponseDto)
	            .orElse(null);
	}

	private RankingPredictionResponseDto convertToResponseDto(RankingPrediction prediction) {
		java.util.List<com.example.rankingPrediction.RankingPredictionResponseDto.TeamRankingDetail> details = new java.util.ArrayList<>();
		
		// 이번 시즌 및 지난 시즌 순위 데이터 가져오기
		java.util.List<Object[]> currentRankings = gameRepository.findTeamRankingsBySeason(prediction.getSeasonYear());
		java.util.List<Object[]> lastRankings = gameRepository.findTeamRankingsBySeason(prediction.getSeasonYear() - 1);
		
		java.util.Map<String, Integer> currentRankMap = new java.util.HashMap<>();
		for (Object[] row : currentRankings) {
			currentRankMap.put((String) row[1], ((Number) row[0]).intValue());
		}
		
		java.util.Map<String, Integer> lastRankMap = new java.util.HashMap<>();
		for (Object[] row : lastRankings) {
			lastRankMap.put((String) row[1], ((Number) row[0]).intValue());
		}

		java.util.Map<String, String> teamNameMap = new java.util.HashMap<>();
		homePageTeamRepository.findAll().forEach(t -> teamNameMap.put(t.getTeamId(), t.getTeamName()));

		for (String teamId : prediction.getPredictionData()) {
			details.add(new com.example.rankingPrediction.RankingPredictionResponseDto.TeamRankingDetail(
				teamId,
				teamNameMap.getOrDefault(teamId, teamId),
				currentRankMap.get(teamId),
				lastRankMap.get(teamId)
			));
		}

		return new RankingPredictionResponseDto(
			prediction.getId(),
			prediction.getUserId(),
			prediction.getSeasonYear(),
			prediction.getPredictionData(),
			details,
			prediction.getCreatedAt()
		);
	}
	
	public int getCurrentSeason() {
		if (!SeasonUtils.isPredictionPeriod()) {
			throw new IllegalStateException(
				"현재는 순위 예측 기간이 아닙니다. (예측 가능 기간: 11월 1일 ~ 5월 31일)"
			);
		}
		return SeasonUtils.getCurrentPredictionSeason();
	}
	
}