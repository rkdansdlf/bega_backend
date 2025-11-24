package com.example.prediction;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// 더미 날짜 자동 업데이트

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchScheduler {

    private final MatchRepository matchRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void updateDummyMatchDates() {
        LocalDate today = LocalDate.now();
        
        // 비시즌 체크 (12월 ~ 3월 21일)
        if (!isOffSeason(today)) {
            log.info("시즌 중이므로 더미 데이터 업데이트 스킵");
            return;
        }
        
        log.info("더미 경기 날짜 업데이트 시작");
        
        LocalDate tomorrow = today.plusDays(1);
        
        List<Match> dummyMatches = matchRepository.findByIsDummy(true);
        
        if (dummyMatches.isEmpty()) {
            log.info("업데이트할 더미 경기가 없습니다.");
            return;
        }
        
        for (Match match : dummyMatches) {
            match.setGameDate(tomorrow);
            log.info("더미 경기 날짜 업데이트: {} -> {}", match.getGameId(), tomorrow);
        }
        
        matchRepository.saveAll(dummyMatches);
        
        log.info("더미 경기 날짜 업데이트 완료: {}건", dummyMatches.size());
    }

    private boolean isOffSeason(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        // 11월 15일 ~ 3월 21일은 비시즌
        return (month == 11 && day >= 15) || month == 12 || month <= 2 || (month == 3 && day < 22);
    }
}
