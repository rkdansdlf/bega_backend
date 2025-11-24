package com.example.prediction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
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
    
    // 오늘 이전 서로 다른 날짜 7일치 경기 조회 (과거순)
    @Transactional(readOnly = true)
    public List<MatchDto> getRecentCompletedGames() {
        LocalDate today = LocalDate.now();
        
        // 1. 서로 다른 날짜 조회 (최신순으로 가져옴)
        List<LocalDate> allDates = matchRepository.findRecentDistinctGameDates(today);
        
        // 2. 최대 7개로 제한
        List<LocalDate> recentDates = allDates.stream()
            .limit(7)
            .collect(Collectors.toList());
        
        // 3. 해당 날짜들의 모든 경기 조회 (오래된 날짜부터)
        if (recentDates.isEmpty()) {
            return List.of();
        }
        
        List<Match> matches = matchRepository.findAllByGameDatesIn(recentDates);
        
        return matches.stream()
            .map(MatchDto::fromEntity)
            .collect(Collectors.toList());
    }
    
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
    
 // 투표 현황 조회 (실시간 + 최종 결과)
    @Transactional(readOnly = true)
    public PredictionResponseDto getVoteStatus(String gameId) {
        // 1. 먼저 최종 결과가 있는지 확인 (과거 경기)
        Optional<VoteFinalResult> finalResult = voteFinalResultRepository.findById(gameId);
        
        if (finalResult.isPresent()) {
            // 과거 경기의 최종 투표 결과 반환
            VoteFinalResult result = finalResult.get();
            int totalVotes = result.getFinalVotesA() + result.getFinalVotesB();
            
            return PredictionResponseDto.builder()
                    .gameId(gameId)
                    .homeVotes((long) result.getFinalVotesA())
                    .awayVotes((long) result.getFinalVotesB())
                    .totalVotes((long) totalVotes)
                    .homePercentage(result.getFinalVotesA())
                    .awayPercentage(result.getFinalVotesB())
                    .build();
        }
        
        // 2. 최종 결과가 없으면 실시간 투표 조회 (미래 경기)
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