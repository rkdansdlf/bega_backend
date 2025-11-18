package com.example.prediction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionService {
    
    private final PredictionRepository predictionRepository;
    private final MatchRepository matchRepository;
    private final PredictionTeamRepository predictionTeamRepository;
		private final VoteFinalResultRepository voteFinalResultRepository;
    
    
    // 투표하기
     
		@Transactional
	    public void vote(Long userId, PredictionRequestDto request) {
	        
	        Match match = matchRepository.findById(request.getGameId())
	                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기 ID입니다."));
	        
	        // 1. 종료된 경기 확인 (과거 경기라면 투표 불가)
	        if (match.isFinished()) {
	            throw new IllegalStateException("이미 종료된 경기는 투표할 수 없습니다.");
	        }
	        
	        Optional<Prediction> existing = predictionRepository
	                .findByGameIdAndUserId(request.getGameId(), userId);
	        
	        if (existing.isPresent()) {
	            Prediction prediction = existing.get();
	            
	            // 2. 같은 팀 재클릭 시 -> 투표 취소 (삭제)
	            if (prediction.getVotedTeam().equals(request.getVotedTeam())) {
	                predictionRepository.delete(prediction);
	                return; 
	            }
	            
	            // 3. 다른 팀 클릭 시 -> 투표 변경 (Update)
	            prediction.updateVotedTeam(request.getVotedTeam());
	            
	        } else {
	            // 4. 처음 투표 시 -> 새 투표 (Insert)
	            Prediction prediction = Prediction.builder()
	                    .gameId(request.getGameId())
	                    .userId(userId)
	                    .votedTeam(request.getVotedTeam())
	                    .build();
	            predictionRepository.save(prediction);
	        }
	    }
    
    
     // 투표 현황 조회
     
    @Transactional(readOnly = true)
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
    	Match match = matchRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("경기 정보를 찾을 수 없습니다."));

        if (match.isFinished()) {
            throw new IllegalStateException("이미 종료된 경기는 투표를 취소할 수 없습니다.");
        }
        
    	Prediction prediction = predictionRepository
                .findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new IllegalStateException("투표 내역이 없습니다."));
        
        predictionRepository.delete(prediction);
    }
    
    // 과거 경기 결과 조회 (스코어 및 결과 포함)
    @Transactional(readOnly = true)
    public List<MatchDto> getPastGames() {
        
    	// 1. 현재 서버 시각 기준 날짜 계산 (오늘 날짜)
        LocalDate today = LocalDate.now();
        final int limit = 7; // 일주일치로 고정
        
        // 2. 조회 기간 설정: 어제(endDate)부터 7일 전(startDate)까지
        LocalDate endDate = today.minusDays(1); // 어제
        LocalDate startDate = today.minusDays(limit); // 7일 전
        
        // 3. Repository 호출 (결과가 확정된 경기만 조회)
        List<Match> pastMatches = matchRepository.findCompletedByDateRange(
                startDate, 
                endDate);
        
        // 4. Match 엔티티를 MatchDto로 변환하여 반환
        return pastMatches.stream()
            .map(MatchDto::fromEntity)
            .collect(Collectors.toList());
    }
    
    // 오늘 경기 목록 조회
     
    @Transactional(readOnly = true)
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