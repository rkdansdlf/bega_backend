package com.example.prediction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PredictionService {
	
	private final PredictionRepository predictionRepository;
	private final MatchRepository matchRepository;
	

	// 투표하기
	
	@Transactional
	public void vote(Long userId,PredictionRequestDto request) {
		// 이미 투표했는지 확인
		Optional<Prediction> existing = predictionRepository
				.findByGameIdAndUserId(request.getGameId(), userId);
		
		if (existing.isPresent()) {
			throw new IllegalStateException("이미 투표하셨습니다."); 
		}
		
		// 새 투표 저장
		Prediction prediction = Prediction.builder()
				.gameId(request.getGameId())
				.userId(userId)
				.votedTeam(request.getVotedTeam())
				.build();
		
		predictionRepository.save(prediction);
	}
	
	
	// 투표 현황 조회
	
	@Transactional
	public PredictionResponseDto getVoteStatus(String gameId) {
		
		Long homeVotes = predictionRepository.countByGameIdAndVotedTeam(gameId, "home");
		Long awayVotes = predictionRepository.countByGameIdAndVotedTeam(gameId, "away");
		Long totalVotes = homeVotes + awayVotes;
		
		// 퍼센트 계산
		int homePercentage = totalVotes > 0 ?
				(int) Math.round((homeVotes * 100.0) / totalVotes) : 0;
		int awayPercentage = totalVotes > 0 ?
				(int) Math.round((awayVotes * 100.0) / totalVotes) : 0;
				
		return PredictionResponseDto.builder()
				.gameId(gameId)
				.homeVotes(homeVotes)
				.awayVotes(awayVotes)
				.totalVotes(totalVotes)
				.homePercentage(homePercentage)
				.awayPercentage(awayPercentage)
				.build();
	}

	// 투표 취소
	
	@Transactional
	public void cancelVote(Long userId, String gameId) {
		Prediction prediction = predictionRepository
				.findByGameIdAndUserId(gameId, userId)
				.orElseThrow(() -> new IllegalStateException("투표 내역이 없습니다."));
		
		predictionRepository.delete(prediction);
		
	}

	// 오늘 경기 목록 조회
	
	@Transactional
	public List<Match> getTodayMatches() {
		LocalDate today = LocalDate.now();
		return matchRepository.findByGameDate(today);
	}

}
