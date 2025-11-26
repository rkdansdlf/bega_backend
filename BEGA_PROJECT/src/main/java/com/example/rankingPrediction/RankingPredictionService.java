package com.example.rankingPrediction;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingPredictionService {

	private final RankingPredictionRepository rankingPredictionRepository;

	// 순위 예측을 저장 (수정 불가, 1회만 가능)
	@Transactional
	public RankingPredictionResponseDto savePrediction(
			RankingPredictionRequestDto requestDto, 
			String userIdString) {

		// 1. 예측 가능 기간 체크
		if (!SeasonUtils.isPredictionPeriod()) {
			throw new IllegalStateException(
				"현재는 순위 예측 기간이 아닙니다. (예측 가능 기간: 11월 1일 ~ 5월 31일)"
			);
		}

		// 2. 현재 시즌 연도 가져오기
		int currentSeasonYear = SeasonUtils.getCurrentPredictionSeason();
				
		// 3. 요청한 시즌이 현재 예측 가능한 시즌인지 확인
			if (requestDto.getSeasonYear() != currentSeasonYear) {
				throw new IllegalStateException(
					"현재는 " + currentSeasonYear + " 시즌만 예측 가능합니다."
			);
		}
		
		// 1. Principal에서 받은 String ID를 Long 타입으로 변환
		Long currentUserId = Long.valueOf(userIdString);

		// 2. 이미 예측했는지 확인
		boolean alreadyPredicted = rankingPredictionRepository
				.existsByUserIdAndSeasonYear(currentUserId, currentSeasonYear);

		// 3. 이미 예측했으면 에러 발생
		if (alreadyPredicted) {
			throw new IllegalStateException(
				"이미 " + currentSeasonYear + " 시즌 순위 예측을 완료하셨습니다. 수정은 불가능합니다."
			);
		}

		// 4. 새로운 예측 생성
		RankingPrediction newPrediction = new RankingPrediction(
				currentUserId,
				currentSeasonYear,
				requestDto.getTeamIdsInOrder()
		);

		// 5. DB에 저장
		RankingPrediction saved = rankingPredictionRepository.save(newPrediction);
		return saved.toDto();
	}

	// 현재 로그인된 사용자의 특정 시즌 예측 조회
	@Transactional(readOnly = true)
	public RankingPredictionResponseDto getPrediction(String userIdString, int seasonYear) {

		// String ID를 Long 타입으로 변환
		Long userId = Long.valueOf(userIdString);

		// userId와 seasonYear를 기준으로 DB에서 데이터 조회
		return rankingPredictionRepository.findByUserIdAndSeasonYear(userId, seasonYear)
				.map(RankingPrediction::toDto)
				.orElse(null);
	}
	
	public int getCurrentSeason() {
		if (!SeasonUtils.isPredictionPeriod()) {
			throw new IllegalStateException(
				"현재는 순위 예측 기간이 아닙니다. (예측 가능 기간: 11월 1일 ~ 5월 31일)"
			);
		}
		return SeasonUtils.getCurrentPredictionSeason();
	}
	
	// 공유용 예측 조회 (userId를 직접 받음)
	@Transactional(readOnly = true)
	public RankingPredictionResponseDto getPredictionByUserIdAndSeason(Long userId, int seasonYear) {
	    return rankingPredictionRepository.findByUserIdAndSeasonYear(userId, seasonYear)
	            .map(RankingPrediction::toDto)
	            .orElse(null);
	}
	
}