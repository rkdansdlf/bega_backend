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
    private final VoteFinalResultRepository voteFinalResultRepository;
    
    
    // 특정 날짜의 경기 조회 
    @Transactional(readOnly = true)
    public List<MatchDto> getMatchesByDate(LocalDate date) {
        List<Match> matches = matchRepository.findByGameDate(date);
        
        return matches.stream()
            .map(MatchDto::fromEntity)
            .collect(Collectors.toList());
    }
    
    
    // 특정 기간의 경기 조회 (과거 경기 일주일치 등)
    @Transactional(readOnly = true)
    public List<MatchDto> getMatchesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Match> matches = matchRepository.findCompletedByDateRange(startDate, endDate);
        
        return matches.stream()
            .map(MatchDto::fromEntity)
            .collect(Collectors.toList());
    }
    
    
    // 투표하기
    @Transactional
    public void vote(Long userId, PredictionRequestDto request) {
        
        Match match = matchRepository.findById(request.getGameId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));
        
        if (match.isFinished()) {
            throw new IllegalStateException("이미 종료된 경기는 투표할 수 없습니다.");
        }
        
        Optional<Prediction> existing = predictionRepository
                .findByGameIdAndUserId(request.getGameId(), userId);
        
        if (existing.isPresent()) {
            Prediction prediction = existing.get();
            
            if (prediction.getVotedTeam().equals(request.getVotedTeam())) {
                predictionRepository.delete(prediction);
                return; 
            }
            
            prediction.updateVotedTeam(request.getVotedTeam());
            
        } else {
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
    
    
    // 최종 투표 결과 저장
    @Transactional
    public void saveFinalVoteResult(String gameId) {
        
        Match match = matchRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));
        
        if (!match.isFinished()) {
            throw new IllegalStateException("종료되지 않은 경기는 최종 결과를 저장할 수 없습니다.");
        }
        
        PredictionResponseDto currentStatus = getVoteStatus(gameId);
        
        int homePercentage = currentStatus.getHomePercentage();
        int awayPercentage = currentStatus.getAwayPercentage();
        
        String finalWinner = "DRAW";
        if (homePercentage > awayPercentage) {
            finalWinner = "HOME";
        } else if (awayPercentage > homePercentage) {
            finalWinner = "AWAY";
        }
        
        VoteFinalResult finalResult = VoteFinalResult.builder()
                .gameId(gameId)
                .finalVotesA(homePercentage)
                .finalVotesB(awayPercentage)
                .finalWinner(finalWinner)
                .build();
        
        voteFinalResultRepository.save(finalResult);
    }
}