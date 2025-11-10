package com.example.prediction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PredictionService {
	
	private final PredictionRepository predictionRepository;
	private final MatchRepository matchRepository;
	private final PredictionTeamRepository predictionTeamRepository;
	private final VoteFinalResultRepository voteFinalResultRepository;
	
	
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
	
	// 특정 날짜의 경기 목록 조회 
    @Transactional
    public List<Match> getMatchesByDate(LocalDate date) {
        return matchRepository.findByGameDate(date);
    }

	
	// 최종 투표 결과 저장
	@Transactional
	public void saveFinalVoteResult(String gameId) {
		
		// 1. Prediction 엔티티를 집계하여 최종 득표수를 계산합니다.
		// getVoteStatus와 동일한 집계 로직을 사용
		PredictionResponseDto currentStatus = getVoteStatus(gameId);
		
		Long totalVotes = currentStatus.getTotalVotes();
		int homePercentage = currentStatus.getHomePercentage();
		int awayPercentage = currentStatus.getAwayPercentage();
		
		// 2. 최종 승리팀 결정
		// 투표 퍼센트가 높은 팀을 승자로 지정
		String finalWinner = "DRAW"; // 기본값
		if (homePercentage > awayPercentage) {
			finalWinner = "HOME";
		} else if (awayPercentage > homePercentage) {
			finalWinner = "AWAY";
		}
		
		// 3. 최종 결과 엔티티 생성 및 저장
		VoteFinalResult finalResult = VoteFinalResult.builder()
				.gameId(gameId)
				.finalVotesA(homePercentage)
				.finalVotesB(awayPercentage)
				.finalWinner(finalWinner)
				.build();
		
		voteFinalResultRepository.save(finalResult);
	}




}
