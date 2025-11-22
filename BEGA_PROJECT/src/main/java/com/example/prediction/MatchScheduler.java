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
        log.info("더미 경기 날짜 업데이트 시작");

        LocalDate today = LocalDate.now(); // tomorrow 대신 today

        List<Match> dummyMatches = matchRepository.findByIsDummy(true);

        if (dummyMatches.isEmpty()) {
            log.info("업데이트할 더미 경기가 없습니다.");
            return;
        }

        for (Match match : dummyMatches) {
            match.setGameDate(today); // today로 변경
            log.info("더미 경기 날짜 업데이트: {} -> {}", match.getGameId(), today);
        }

        matchRepository.saveAll(dummyMatches);

        log.info("더미 경기 날짜 업데이트 완료: {}건", dummyMatches.size());
    }
}
