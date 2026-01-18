package com.example.BegaDiary.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.BegaDiary.DiaryEmoji;
import com.example.BegaDiary.Entity.BegaDiary.DiaryType;
import com.example.BegaDiary.Entity.BegaDiary.DiaryWinning;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Entity.DiaryStatisticsDto;
import com.example.BegaDiary.Exception.DiaryAlreadyExistsException;
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Exception.GameNotFoundException;
import com.example.BegaDiary.Exception.ImageProcessingException;
import com.example.BegaDiary.Exception.WinningNameNotFoundException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Utils.BaseballConstants;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.GameEntity;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.mate.repository.PartyApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BegaDiaryService {

    private final BegaDiaryRepository diaryRepository;
    private final BegaGameService gameService;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final CheerPostRepo cheerPostRepository;
    private final PartyApplicationRepository partyApplicationRepository;

    // 전체 다이어리 조회
    public List<DiaryResponseDto> getAllDiaries(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        List<BegaDiary> diaries = this.diaryRepository.findByUserId(userId);

        return diaries.stream()
                .map(diary -> {
                    List<String> signedUrls = null;
                    if (diary.getPhotoUrls() != null && !diary.getPhotoUrls().isEmpty()) {
                        try {
                            signedUrls = imageService
                                    .getDiaryImageSignedUrls(diary.getPhotoUrls())
                                    .block();
                        } catch (Exception e) {
                            Long diaryId = diary.getId();
                            log.error("다이어리 이미지 Signed URL 생성 실패: diaryId={}, error={}",
                                    diaryId != null ? diaryId : "unknown", e.getMessage());
                            signedUrls = new ArrayList<>();
                        }
                    }

                    return DiaryResponseDto.from(diary, signedUrls);
                })
                .collect(Collectors.toList());
    }

    // 특정 다이어리 조회
    public DiaryResponseDto getDiaryById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        BegaDiary diary = this.diaryRepository.findById(id)
                .orElseThrow(() -> new DiaryNotFoundException(id));

        List<String> signedUrls = null;
        if (diary.getPhotoUrls() != null && !diary.getPhotoUrls().isEmpty()) {
            try {
                signedUrls = imageService
                        .getDiaryImageSignedUrls(diary.getPhotoUrls())
                        .block();
            } catch (Exception e) {
                log.warn("Failed to generate signed URLs for diary {}", id);
            }
        }

        return DiaryResponseDto.from(diary, signedUrls);
    }

    // 다이어리 저장
    @Transactional
    public BegaDiary save(Long userId, DiaryRequestDto requestDto) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // 1. 날짜 파싱
        LocalDate diaryDate = LocalDate.parse(requestDto.getDate());

        UserEntity user = this.userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 2. 중복 체크
        if (diaryRepository.existsByUserAndDiaryDate(user, diaryDate)) {
            throw new DiaryAlreadyExistsException(
                    diaryDate + "에 이미 다이어리가 작성되어 있습니다.");
        }

        // 3. Enum 변환
        DiaryEmoji mood = DiaryEmoji.fromKoreanName(requestDto.getEmojiName());
        DiaryWinning winning = null;

        try {
            winning = DiaryWinning.valueOf(requestDto.getWinningName());
        } catch (IllegalArgumentException e) {
            throw new WinningNameNotFoundException();
        }

        // 4. 빌더로 엔티티 생성
        if (requestDto.getGameId() == null) {
            throw new GameNotFoundException();
        }

        GameEntity game = gameService.getGameById(requestDto.getGameId());

        String homeTeamKorean = BaseballConstants.getTeamKoreanName(game.getHomeTeam());
        String awayTeamKorean = BaseballConstants.getTeamKoreanName(game.getAwayTeam());
        String team = homeTeamKorean + " vs " + awayTeamKorean;

        BegaDiary diary = BegaDiary.builder()
                .diaryDate(diaryDate)
                .memo(requestDto.getMemo())
                .mood(mood)
                .type(DiaryType.ATTENDED)
                .winning(winning)
                .photoUrls(requestDto.getPhotos())
                .game(game)
                .team(team)
                .stadium(game.getStadium())
                .user(user)
                .section(requestDto.getSection())
                .block(requestDto.getBlock())
                .seatRow(requestDto.getSeatRow())
                .seatNumber(requestDto.getSeatNumber())
                .build();

        // 5. DB 저장
        return diaryRepository.save(Objects.requireNonNull(diary));
    }

    @Async
    @Transactional
    public CompletableFuture<List<String>> addImages(Long diaryId, Long userId, List<MultipartFile> images) {
        if (diaryId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Diary ID cannot be null"));
        }
        if (images == null || images.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        try {
            // 다이어리 조회
            BegaDiary diary = diaryRepository.findById(diaryId)
                    .orElseThrow(() -> new DiaryNotFoundException(diaryId));

            // 이미지 업로드
            List<String> uploadedPaths = imageService.uploadDiaryImages(userId, diaryId, images)
                    .block();

            if (uploadedPaths == null || uploadedPaths.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }

            // DB 업데이트 (기존 이미지 + 새 이미지)
            List<String> allPaths = new ArrayList<>();
            if (diary.getPhotoUrls() != null) {
                allPaths.addAll(diary.getPhotoUrls());
            }
            allPaths.addAll(uploadedPaths);
            diary.updateDiary(diary.getMemo(), diary.getMood(), allPaths);

            diaryRepository.save(diary);
            log.info("✅ [Async] 다이어리 이미지 업로드 및 DB 업데이트 성공: diaryId={}, 총 경로 수={}", diaryId, allPaths.size());
            return CompletableFuture.completedFuture(uploadedPaths);

        } catch (Exception e) {
            log.error("❌ [Async] 다이어리 이미지 추가 중 치명적 오류 발생: diaryId={}, error={}", diaryId, e.getMessage(), e);
            return CompletableFuture.failedFuture(new ImageProcessingException(e.getMessage()));
        }
    }

    // 다이어리 수정
    @Transactional
    public BegaDiary update(Long id, DiaryRequestDto requestDto) {
        if (id == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        BegaDiary diary = this.diaryRepository.findById(id)
                .orElseThrow(() -> new DiaryNotFoundException(id));

        DiaryEmoji mood = DiaryEmoji.fromKoreanName(requestDto.getEmojiName());

        diary.updateDiary(
                requestDto.getMemo(),
                mood,
                requestDto.getPhotos());

        return diary;
    }

    // 다이어리 삭제
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        BegaDiary diary = this.diaryRepository.findById(id)
                .orElseThrow(() -> new DiaryNotFoundException(id));

        if (diary.getPhotoUrls() != null && !diary.getPhotoUrls().isEmpty()) {
            try {
                imageService.deleteDiaryImages(diary.getPhotoUrls()).block();
            } catch (Exception e) {
                log.error("이미지 삭제 실패 (다이어리는 삭제됨): diaryId={}", id, e);
            }
        }

        this.diaryRepository.delete(diary);
    }

    public DiaryStatisticsDto getStatistics(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        List<BegaDiary> diaries = diaryRepository.findByUserId(userId);

        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();

        int totalCount = diaryRepository.countByUserId(userId);
        int totalWins = diaryRepository.countByUserIdAndWinning(userId, DiaryWinning.WIN);
        int totalLosses = diaryRepository.countByUserIdAndWinning(userId, DiaryWinning.LOSE);
        int totalDraws = diaryRepository.countByUserIdAndWinning(userId, DiaryWinning.DRAW);

        int monthlyCount = diaryRepository.countByUserIdAndYearAndMonth(userId, currentYear, currentMonth);
        int yearlyCount = diaryRepository.countByUserIdAndYear(userId, currentYear);
        int yearlyWins = diaryRepository.countYearlyWins(userId, currentYear);

        double winRate = totalCount > 0 ? (double) totalWins / totalCount * 100 : 0;
        double yearlyWinRate = yearlyCount > 0 ? (double) yearlyWins / yearlyCount * 100 : 0;

        List<Object[]> stadiumResult = diaryRepository.findMostVisitedStadium(userId);
        String mostVisitedStadium = null;
        int mostVisitedCount = 0;
        if (!stadiumResult.isEmpty() && stadiumResult.get(0).length >= 2) {
            String stadiumShortName = (String) stadiumResult.get(0)[0];
            mostVisitedStadium = BaseballConstants.getFullStadiumName(stadiumShortName);
            mostVisitedCount = ((Number) stadiumResult.get(0)[1]).intValue();
        }

        List<Object[]> monthResult = diaryRepository.findHappiestMonth(userId);
        String happiestMonth = null;
        int happiestCount = 0;
        if (!monthResult.isEmpty() && monthResult.get(0).length >= 2) {
            int month = ((Number) monthResult.get(0)[0]).intValue();
            happiestMonth = month + "월";
            happiestCount = ((Number) monthResult.get(0)[1]).intValue();
        }

        LocalDate firstDate = diaryRepository.findFirstDiaryDate(userId);
        String firstDiaryDate = firstDate != null ? firstDate.toString() : null;

        int cheerPostCount = cheerPostRepository.countByUserId(userId);
        int mateParticipationCount = partyApplicationRepository.countCheckedInPartiesByUserId(userId);

        // --- New Logic Start ---

        // 1. Sort diaries by date for streak analysis
        List<BegaDiary> sortedDiaries = new ArrayList<>(diaries);
        sortedDiaries.sort((d1, d2) -> d1.getDiaryDate().compareTo(d2.getDiaryDate()));

        // 2. Streak Analysis
        int currentWinStreak = 0;
        int currentLossStreak = 0;
        int longestWinStreak = 0;

        int tempWinStreak = 0;

        for (BegaDiary diary : sortedDiaries) {
            if (diary.getWinning() == DiaryWinning.WIN) {
                tempWinStreak++;
                longestWinStreak = Math.max(longestWinStreak, tempWinStreak);
            } else {
                tempWinStreak = 0;
            }
        }

        // Calculate current streaks (counting backwards from most recent)
        for (int i = sortedDiaries.size() - 1; i >= 0; i--) {
            BegaDiary diary = sortedDiaries.get(i);
            if (diary.getWinning() == DiaryWinning.WIN) {
                if (currentLossStreak == 0)
                    currentWinStreak++;
                else
                    break;
            } else if (diary.getWinning() == DiaryWinning.LOSE) {
                if (currentWinStreak == 0)
                    currentLossStreak++;
                else
                    break;
            } else {
                break; // Draw breaks current streak count for simplicity
            }
        }

        // 3. Opponent & Day Analysis
        Map<String, DiaryStatisticsDto.OpponentStats> opponentStatsMap = new java.util.HashMap<>();
        Map<String, DiaryStatisticsDto.DayStats> dayStatsMap = new java.util.HashMap<>();

        // Assuming current user's favorite team is consistent for simplicity
        // In a real scenario, we might want to store "my team" in the diary entity
        UserEntity user = userRepository.findById(userId).orElse(null);
        String myTeamCode = user != null && user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamName() : "";
        if (myTeamCode == null)
            myTeamCode = "";

        for (BegaDiary diary : diaries) {
            // Opponent Analysis
            if (!myTeamCode.isEmpty() && diary.getGame() != null) {
                String home = diary.getGame().getHomeTeam();
                String away = diary.getGame().getAwayTeam();
                String opponent;

                // Simple check: if home is my team, away is opponent, etc.
                // Note: Team codes in DB might differ (e.g., 'Doosan' vs 'OB'), assuming
                // standard codes
                if (myTeamCode.equals(home))
                    opponent = BaseballConstants.getTeamKoreanName(away);
                else if (myTeamCode.equals(away))
                    opponent = BaseballConstants.getTeamKoreanName(home);
                else {
                    // If my team isn't playing, maybe just record the one I didn't pick?
                    // Or skip. For now, we skip neutral games for opponent stats
                    opponent = null;
                }

                if (opponent != null) {
                    DiaryStatisticsDto.OpponentStats stats = opponentStatsMap.getOrDefault(opponent,
                            DiaryStatisticsDto.OpponentStats.builder().wins(0).losses(0).draws(0).build());
                    if (diary.getWinning() == DiaryWinning.WIN)
                        stats.setWins(stats.getWins() + 1);
                    else if (diary.getWinning() == DiaryWinning.LOSE)
                        stats.setLosses(stats.getLosses() + 1);
                    else
                        stats.setDraws(stats.getDraws() + 1);
                    opponentStatsMap.put(opponent, stats);
                }
            }

            // Day Analysis
            String dayOfWeek = getDayOfWeekKorean(diary.getDiaryDate().getDayOfWeek());
            DiaryStatisticsDto.DayStats dStats = dayStatsMap.getOrDefault(dayOfWeek,
                    DiaryStatisticsDto.DayStats.builder().count(0).wins(0).build());
            dStats.setCount(dStats.getCount() + 1);
            if (diary.getWinning() == DiaryWinning.WIN)
                dStats.setWins(dStats.getWins() + 1);
            dayStatsMap.put(dayOfWeek, dStats);
        }

        // Calculate rates
        opponentStatsMap.values().forEach(s -> {
            int total = s.getWins() + s.getLosses() + s.getDraws();
            s.setWinRate(total > 0 ? (double) s.getWins() / total * 100 : 0);
        });

        dayStatsMap.values().forEach(s -> {
            s.setWinRate(s.getCount() > 0 ? (double) s.getWins() / s.getCount() * 100 : 0);
        });

        // Find Best/Worst/Lucky
        String bestOpponent = opponentStatsMap.entrySet().stream()
                .filter(e -> (e.getValue().getWins() + e.getValue().getLosses() + e.getValue().getDraws()) >= 2) // Min
                                                                                                                 // 2
                                                                                                                 // games
                .max((e1, e2) -> Double.compare(e1.getValue().getWinRate(), e2.getValue().getWinRate()))
                .map(Map.Entry::getKey).orElse("-");

        String worstOpponent = opponentStatsMap.entrySet().stream()
                .filter(e -> (e.getValue().getWins() + e.getValue().getLosses() + e.getValue().getDraws()) >= 2)
                .min((e1, e2) -> Double.compare(e1.getValue().getWinRate(), e2.getValue().getWinRate()))
                .map(Map.Entry::getKey).orElse("-");

        String luckyDay = dayStatsMap.entrySet().stream()
                .max((e1, e2) -> Double.compare(e1.getValue().getWinRate(), e2.getValue().getWinRate()))
                .map(Map.Entry::getKey).orElse("-");

        // 4. Badges
        List<String> earnedBadges = new ArrayList<>();
        if (totalCount >= 1)
            earnedBadges.add("ticket"); // 첫 직관
        if (totalCount >= 10)
            earnedBadges.add("flame"); // 불꽃 응원단

        // Count unique stadiums
        long uniqueStadiums = diaries.stream().map(BegaDiary::getStadium).distinct().count();
        if (uniqueStadiums >= 3)
            earnedBadges.add("map-pin"); // 구장 마스터

        if (totalCount >= 10 && winRate >= 60.0)
            earnedBadges.add("sparkles"); // 승리요정
        if (totalCount >= 50)
            earnedBadges.add("crown"); // 레전드

        // --- New Logic End ---

        Map<String, Long> emojiCounts = diaries.stream()
                .collect(Collectors.groupingBy(
                        diary -> diary.getMood().getKoreanName(),
                        Collectors.counting()));

        return DiaryStatisticsDto.builder()
                .totalCount(totalCount)
                .totalWins(totalWins)
                .totalLosses(totalLosses)
                .totalDraws(totalDraws)
                .winRate(Math.round(winRate * 10) / 10.0)
                .monthlyCount(monthlyCount)
                .yearlyCount(yearlyCount)
                .yearlyWins(yearlyWins)
                .yearlyWinRate(Math.round(yearlyWinRate * 10) / 10.0)
                .mostVisitedStadium(mostVisitedStadium)
                .mostVisitedCount(mostVisitedCount)
                .happiestMonth(happiestMonth)
                .happiestCount(happiestCount)
                .firstDiaryDate(firstDiaryDate)
                .cheerPostCount(cheerPostCount)
                .mateParticipationCount(mateParticipationCount)
                .emojiCounts(emojiCounts)
                .currentWinStreak(currentWinStreak)
                .longestWinStreak(longestWinStreak)
                .currentLossStreak(currentLossStreak)
                .opponentWinRates(opponentStatsMap)
                .bestOpponent(bestOpponent)
                .worstOpponent(worstOpponent)
                .dayOfWeekStats(dayStatsMap)
                .luckyDay(luckyDay)
                .earnedBadges(earnedBadges)
                .build();
    }

    private String getDayOfWeekKorean(java.time.DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return "월";
            case TUESDAY:
                return "화";
            case WEDNESDAY:
                return "수";
            case THURSDAY:
                return "목";
            case FRIDAY:
                return "금";
            case SATURDAY:
                return "토";
            case SUNDAY:
                return "일";
            default:
                return "";
        }
    }
}