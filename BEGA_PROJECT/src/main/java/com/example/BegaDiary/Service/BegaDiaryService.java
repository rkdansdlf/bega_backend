package com.example.BegaDiary.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.BegaDiary.DiaryEmoji;
import com.example.BegaDiary.Entity.BegaDiary.DiaryType;
import com.example.BegaDiary.Entity.BegaDiary.DiaryWinning;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Entity.DiaryStatisticsDto;
import com.example.BegaDiary.Entity.SeatViewPhotoDto;
import com.example.BegaDiary.Exception.DiaryAlreadyExistsException;
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Exception.GameNotFoundException;
import com.example.BegaDiary.Exception.ImageProcessingException;
import com.example.BegaDiary.Exception.WinningNameNotFoundException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.DiaryStatisticsRow;
import com.example.BegaDiary.Utils.BaseballConstants;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.common.config.CacheConfig;
import com.example.kbo.entity.GameEntity;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.media.entity.MediaDomain;
import com.example.media.service.MediaLinkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BegaDiaryService {
    private static final DiaryType DEFAULT_DIARY_TYPE = DiaryType.ATTENDED;
    private static final DiaryEmoji DEFAULT_SCHEDULED_MOOD = DiaryEmoji.HAPPY;

    private final BegaDiaryRepository diaryRepository;
    private final BegaGameService gameService;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final CheerPostRepo cheerPostRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private final TicketVerificationTokenStore ticketVerificationTokenStore;
    private final SeatViewService seatViewService;
    private final MediaLinkService mediaLinkService;

    // 전체 다이어리 조회
    public List<DiaryResponseDto> getAllDiaries(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        List<BegaDiary> diaries = this.diaryRepository.findByUserId(userId);

        List<List<String>> signedUrlsByDiary = resolveDiarySignedUrls(diaries);
        List<DiaryResponseDto> responses = new ArrayList<>(diaries.size());
        for (int index = 0; index < diaries.size(); index++) {
            responses.add(DiaryResponseDto.from(diaries.get(index), signedUrlsByDiary.get(index)));
        }

        return Objects.requireNonNull(responses);
    }

    /**
     * Raw lookup is reserved for trusted internal/admin maintenance paths.
     * User-facing flows must use getOwnedDiaryEntity(id, userId) so missing and
     * non-owned records share the same not-found response.
     */
    @Deprecated(forRemoval = false)
    public BegaDiary getDiaryEntityById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        return this.diaryRepository.findById(id)
                .orElseThrow(() -> new DiaryNotFoundException(id));
    }

    public BegaDiary getOwnedDiaryEntity(Long id, Long userId) {
        return requireOwnedDiary(id, userId);
    }

    // 특정 다이어리 조회
    public DiaryResponseDto getDiaryById(Long id, Long userId) {
        if (id == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        BegaDiary diary = requireOwnedDiary(id, userId);

        List<List<String>> signedUrls = resolveDiarySignedUrls(List.of(diary));
        return Objects.requireNonNull(DiaryResponseDto.from(diary, signedUrls.isEmpty() ? null : signedUrls.get(0)));
    }

    // 다이어리 저장
    @CacheEvict(value = CacheConfig.DIARY_STATS, key = "#userId")
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
        DiaryType diaryType = resolveDiaryType(requestDto.getType(), DEFAULT_DIARY_TYPE);
        DiaryEmoji mood = resolveMood(requestDto.getEmojiName(), diaryType);
        DiaryWinning winning = resolveWinning(diaryType, requestDto.getWinningName());

        // 4. 빌더로 엔티티 생성
        if (requestDto.getGameId() == null) {
            throw new GameNotFoundException();
        }

        GameEntity game = gameService.getGameById(requestDto.getGameId());
        if (game == null) {
            throw new GameNotFoundException();
        }

        String team = buildTeamLabel(game);

        List<String> normalizedPhotoPaths = diaryType == DiaryType.ATTENDED
                ? normalizeDiaryPhotoPathsForCreate(requestDto.getPhotos(), userId)
                : List.of();
        mediaLinkService.resolveReadyAssets(userId, MediaDomain.DIARY, normalizedPhotoPaths);

        BegaDiary diary = BegaDiary.builder()
                .diaryDate(diaryDate)
                .memo(requestDto.getMemo())
                .mood(mood)
                .type(diaryType)
                .winning(winning)
                .photoUrls(normalizedPhotoPaths)
                .game(game)
                .team(team)
                .stadium(game.getStadium())
                .user(user)
                .section(diaryType == DiaryType.ATTENDED ? requestDto.getSection() : null)
                .block(diaryType == DiaryType.ATTENDED ? requestDto.getBlock() : null)
                .seatRow(diaryType == DiaryType.ATTENDED ? requestDto.getSeatRow() : null)
                .seatNumber(diaryType == DiaryType.ATTENDED ? requestDto.getSeatNumber() : null)
                .build();

        if (diaryType == DiaryType.ATTENDED) {
            applyTicketVerification(diary, game, diaryDate, requestDto.getTicketVerificationToken());
        }

        // 5. DB 저장
        BegaDiary savedDiary = Objects.requireNonNull(diaryRepository.save(Objects.requireNonNull(diary)));
        mediaLinkService.syncDiaryLinks(savedDiary.getId(), userId, normalizedPhotoPaths);
        return savedDiary;
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
            BegaDiary diary = requireOwnedDiary(diaryId, userId);

            // 이미지 업로드
            List<String> uploadedPaths = imageService.uploadDiaryImages(userId, diaryId, images)
                    .block();

            if (uploadedPaths == null || uploadedPaths.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }

            // DB 업데이트 (기존 이미지 + 새 이미지)
            List<String> allPaths = new ArrayList<>();
            if (diary.getPhotoUrls() != null) {
                allPaths.addAll(normalizeDiaryPhotoPathsForUpdate(diary.getPhotoUrls(), userId, diaryId));
            }
            allPaths.addAll(uploadedPaths);
            diary.updateDiary(
                    diary.getMemo(),
                    diary.getMood(),
                    diary.getType(),
                    allPaths,
                    diary.getGame(),
                    diary.getTeam(),
                    diary.getStadium(),
                    diary.getWinning(),
                    diary.getSection(),
                    diary.getBlock(),
                    diary.getSeatRow(),
                    diary.getSeatNumber());

            diaryRepository.save(diary);
            log.info("✅ [Async] 다이어리 이미지 업로드 및 DB 업데이트 성공: diaryId={}, 총 경로 수={}", diaryId, allPaths.size());
            return CompletableFuture.completedFuture(Objects.requireNonNull(uploadedPaths));

        } catch (Exception e) {
            log.error("❌ [Async] 다이어리 이미지 추가 중 치명적 오류 발생: diaryId={}, error={}", diaryId, e.getMessage(), e);
            return CompletableFuture.failedFuture(new ImageProcessingException(e.getMessage()));
        }
    }

    // 다이어리 수정
    @CacheEvict(value = CacheConfig.DIARY_STATS, key = "#userId")
    @Transactional
    public BegaDiary update(Long id, Long userId, DiaryRequestDto requestDto) {
        if (id == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        BegaDiary diary = requireOwnedDiary(id, userId);

        DiaryType diaryType = resolveDiaryType(requestDto.getType(), diary.getType());
        DiaryEmoji mood = resolveMood(requestDto.getEmojiName(), diaryType);
        DiaryWinning winning = resolveWinning(diaryType, requestDto.getWinningName());
        GameEntity game = diary.getGame();
        if (requestDto.getGameId() != null) {
            game = gameService.getGameById(requestDto.getGameId());
            if (game == null) {
                throw new GameNotFoundException();
            }
        }

        String updatedTeam = game != null ? buildTeamLabel(game) : diary.getTeam();
        String updatedStadium = game != null ? game.getStadium() : diary.getStadium();
        boolean identityChanged = hasTicketIdentityChanged(diary, game, updatedStadium);

        List<String> normalizedPhotoPaths = diaryType == DiaryType.ATTENDED
                ? normalizeDiaryPhotoPathsForUpdate(requestDto.getPhotos(), userId, diary.getId())
                : List.of();
        mediaLinkService.resolveReadyAssets(userId, MediaDomain.DIARY, normalizedPhotoPaths);

        diary.updateDiary(
                requestDto.getMemo(),
                mood,
                diaryType,
                normalizedPhotoPaths,
                game,
                updatedTeam,
                updatedStadium,
                winning,
                diaryType == DiaryType.ATTENDED ? requestDto.getSection() : null,
                diaryType == DiaryType.ATTENDED ? requestDto.getBlock() : null,
                diaryType == DiaryType.ATTENDED ? requestDto.getSeatRow() : null,
                diaryType == DiaryType.ATTENDED ? requestDto.getSeatNumber() : null);

        if (diaryType == DiaryType.ATTENDED && StringUtils.hasText(requestDto.getTicketVerificationToken())) {
            applyTicketVerification(diary, game, diary.getDiaryDate(), requestDto.getTicketVerificationToken());
        } else if (diaryType == DiaryType.SCHEDULED || identityChanged) {
            diary.clearTicketVerification();
        }

        if (diaryType == DiaryType.ATTENDED) {
            seatViewService.processDiaryRewardIfEligible(diary);
        }
        mediaLinkService.syncDiaryLinks(diary.getId(), userId, normalizedPhotoPaths);

        return Objects.requireNonNull(diary);
    }

    // 다이어리 삭제
    @CacheEvict(value = CacheConfig.DIARY_STATS, key = "#userId")
    @Transactional
    public void delete(Long id, Long userId) {
        if (id == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        BegaDiary diary = requireOwnedDiary(id, userId);

        if (diary.getPhotoUrls() != null && !diary.getPhotoUrls().isEmpty()) {
            try {
                imageService.deleteDiaryImages(diary.getPhotoUrls(), userId, diary.getId()).block();
            } catch (Exception e) {
                log.error("이미지 삭제 실패 (다이어리는 삭제됨): diaryId={}", id, e);
            }
        }

        seatViewService.deleteByDiaryId(id);
        mediaLinkService.unlinkEntity(MediaDomain.DIARY, id);
        this.diaryRepository.delete(diary);
    }

    @Cacheable(value = CacheConfig.DIARY_STATS, key = "#userId", sync = true)
    public DiaryStatisticsDto getStatistics(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        List<DiaryStatisticsRow> diaries = diaryRepository.findStatisticsRowsByUserIdOrderByDiaryDateDesc(userId);
        return buildStatistics(userId, diaries);
    }

    private DiaryStatisticsDto buildStatistics(Long userId, List<DiaryStatisticsRow> diaries) {
        List<DiaryStatisticsRow> attendedDiaries = diaries.stream()
                .filter(diary -> diary.getType() == DiaryType.ATTENDED)
                .toList();
        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();
        List<DiaryStatisticsRow> currentYearAttendedDiaries = attendedDiaries.stream()
                .filter(diary -> diary.getDiaryDate() != null)
                .filter(diary -> diary.getDiaryDate().getYear() == currentYear)
                .toList();
        int scheduledCount = (int) diaries.stream()
                .filter(diary -> diary.getType() == DiaryType.SCHEDULED)
                .filter(diary -> diary.getDiaryDate() != null)
                .filter(diary -> diary.getDiaryDate().getYear() == currentYear)
                .count();

        int totalCount = attendedDiaries.size();
        int totalWins = (int) attendedDiaries.stream()
                .filter(diary -> diary.getWinning() == DiaryWinning.WIN)
                .count();
        int totalLosses = (int) attendedDiaries.stream()
                .filter(diary -> diary.getWinning() == DiaryWinning.LOSE)
                .count();
        int totalDraws = (int) attendedDiaries.stream()
                .filter(diary -> diary.getWinning() == DiaryWinning.DRAW)
                .count();

        int monthlyCount = (int) attendedDiaries.stream()
                .filter(diary -> diary.getDiaryDate() != null)
                .filter(diary -> diary.getDiaryDate().getYear() == currentYear)
                .filter(diary -> diary.getDiaryDate().getMonthValue() == currentMonth)
                .count();
        int yearlyCount = (int) attendedDiaries.stream()
                .filter(diary -> diary.getDiaryDate() != null)
                .filter(diary -> diary.getDiaryDate().getYear() == currentYear)
                .count();
        int yearlyWins = (int) attendedDiaries.stream()
                .filter(diary -> diary.getDiaryDate() != null)
                .filter(diary -> diary.getDiaryDate().getYear() == currentYear)
                .filter(diary -> diary.getWinning() == DiaryWinning.WIN)
                .count();

        double winRate = totalCount > 0 ? (double) totalWins / totalCount * 100 : 0;
        double yearlyWinRate = yearlyCount > 0 ? (double) yearlyWins / yearlyCount * 100 : 0;

        Map<String, Long> stadiumCounts = attendedDiaries.stream()
                .map(DiaryStatisticsRow::getStadium)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(stadium -> stadium, Collectors.counting()));
        Map.Entry<String, Long> stadiumResult = stadiumCounts.entrySet().stream()
                .max((left, right) -> Long.compare(left.getValue(), right.getValue()))
                .orElse(null);
        String mostVisitedStadium = null;
        int mostVisitedCount = 0;
        if (stadiumResult != null) {
            String stadiumShortName = stadiumResult.getKey();
            mostVisitedStadium = BaseballConstants.getFullStadiumName(stadiumShortName);
            mostVisitedCount = stadiumResult.getValue().intValue();
        }
        Map<Integer, Integer> monthlyVisitCounts = currentYearAttendedDiaries.stream()
                .collect(Collectors.groupingBy(
                        diary -> diary.getDiaryDate().getMonthValue(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().intValue(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, Integer> stadiumVisitCounts = stadiumCounts.entrySet().stream()
                .sorted((left, right) -> {
                    int countComparison = Long.compare(right.getValue(), left.getValue());
                    if (countComparison != 0) {
                        return countComparison;
                    }
                    return BaseballConstants.getFullStadiumName(left.getKey())
                            .compareTo(BaseballConstants.getFullStadiumName(right.getKey()));
                })
                .collect(Collectors.toMap(
                        entry -> BaseballConstants.getFullStadiumName(entry.getKey()),
                        entry -> entry.getValue().intValue(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<Integer, Long> monthCounts = attendedDiaries.stream()
                .filter(diary -> diary.getDiaryDate() != null)
                .filter(diary -> diary.getMood() == DiaryEmoji.BEST)
                .collect(Collectors.groupingBy(
                        diary -> diary.getDiaryDate().getMonthValue(),
                        Collectors.counting()));
        Map.Entry<Integer, Long> monthResult = monthCounts.entrySet().stream()
                .max((left, right) -> Long.compare(left.getValue(), right.getValue()))
                .orElse(null);
        String happiestMonth = null;
        int happiestCount = 0;
        if (monthResult != null) {
            happiestMonth = monthResult.getKey() + "월";
            happiestCount = monthResult.getValue().intValue();
        }

        LocalDate firstDate = attendedDiaries.stream()
                .map(DiaryStatisticsRow::getDiaryDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        String firstDiaryDate = firstDate != null ? firstDate.toString() : null;

        int cheerPostCount = cheerPostRepository.countByUserId(userId);
        int mateParticipationCount = partyApplicationRepository.countCheckedInPartiesByUserId(userId);

        // --- New Logic Start ---

        // 1. Sort diaries by date for streak analysis (null-safe)
        List<DiaryStatisticsRow> sortedDiaries = new ArrayList<>(attendedDiaries);
        sortedDiaries.sort(Comparator.comparing(
                DiaryStatisticsRow::getDiaryDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // 2. Streak Analysis
        int currentWinStreak = 0;
        int currentLossStreak = 0;
        int longestWinStreak = 0;

        int tempWinStreak = 0;

        for (DiaryStatisticsRow diary : sortedDiaries) {
            if (diary.getWinning() == DiaryWinning.WIN) {
                tempWinStreak++;
                longestWinStreak = Math.max(longestWinStreak, tempWinStreak);
            } else {
                tempWinStreak = 0;
            }
        }

        // Calculate current streaks (counting backwards from most recent)
        for (int i = sortedDiaries.size() - 1; i >= 0; i--) {
            DiaryStatisticsRow diary = sortedDiaries.get(i);
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

        String myTeamCode = diaries.stream()
                .map(DiaryStatisticsRow::getFavoriteTeamId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        final String favoriteTeamCode = myTeamCode;
        int homeVisitCount = favoriteTeamCode.isEmpty() ? 0 : (int) currentYearAttendedDiaries.stream()
                .filter(diary -> favoriteTeamCode.equals(diary.getHomeTeam()))
                .count();
        int awayVisitCount = favoriteTeamCode.isEmpty() ? 0 : (int) currentYearAttendedDiaries.stream()
                .filter(diary -> favoriteTeamCode.equals(diary.getAwayTeam()))
                .count();

        for (DiaryStatisticsRow diary : attendedDiaries) {
            // Opponent Analysis
            if (!myTeamCode.isEmpty() && (diary.getHomeTeam() != null || diary.getAwayTeam() != null)) {
                String home = diary.getHomeTeam();
                String away = diary.getAwayTeam();
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
        long uniqueStadiums = attendedDiaries.stream().map(DiaryStatisticsRow::getStadium).distinct().count();
        if (uniqueStadiums >= 3)
            earnedBadges.add("map-pin"); // 구장 마스터

        if (totalCount >= 10 && winRate >= 60.0)
            earnedBadges.add("sparkles"); // 승리요정
        if (totalCount >= 50)
            earnedBadges.add("crown"); // 레전드

        // --- New Logic End ---

        Map<String, Long> emojiCounts = attendedDiaries.stream()
                .collect(Collectors.groupingBy(
                        diary -> diary.getMood().getKoreanName(),
                        Collectors.counting()));

        return Objects.requireNonNull(DiaryStatisticsDto.builder()
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
                .monthlyVisitCounts(monthlyVisitCounts)
                .stadiumVisitCounts(stadiumVisitCounts)
                .homeVisitCount(homeVisitCount)
                .awayVisitCount(awayVisitCount)
                .scheduledCount(scheduledCount)
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
                .build());
    }

    private List<String> normalizeDiaryPhotoPathsForCreate(List<String> photoPaths, Long userId) {
        if (photoPaths == null || photoPaths.isEmpty()) {
            return List.of();
        }
        return imageService.normalizeDiaryStoragePathsForWrite(photoPaths, userId, null, false);
    }

    private List<String> normalizeDiaryPhotoPathsForUpdate(List<String> photoPaths, Long userId, Long diaryId) {
        if (photoPaths == null || photoPaths.isEmpty()) {
            return List.of();
        }
        return imageService.normalizeDiaryStoragePathsForWrite(photoPaths, userId, diaryId, true);
    }

    private BegaDiary requireOwnedDiary(Long diaryId, Long userId) {
        if (diaryId == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        if (userId == null) {
            throw new DiaryNotFoundException(diaryId);
        }
        return diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new DiaryNotFoundException(diaryId));
    }

    private List<List<String>> resolveDiarySignedUrls(List<BegaDiary> diaries) {
        List<List<String>> resolved = new ArrayList<>(diaries.size());

        for (int index = 0; index < diaries.size(); index++) {
            BegaDiary diary = diaries.get(index);
            List<String> photoUrls = diary.getPhotoUrls();
            int photoCount = photoUrls == null ? 0 : photoUrls.size();
            resolved.add(new ArrayList<>());
            if (photoCount <= 0) {
                continue;
            }

            Long ownerUserId = diary.getUser() != null ? diary.getUser().getId() : null;
            try {
                List<String> signedUrls = imageService
                        .getDiaryImageSignedUrls(photoUrls, ownerUserId, diary.getId())
                        .block();
                resolved.set(index, signedUrls == null ? new ArrayList<>() : new ArrayList<>(signedUrls));
            } catch (Exception e) {
                log.error("다이어리 이미지 Signed URL 생성 실패: diaryId={}, error={}",
                        diary.getId(), e.getMessage(), e);
                resolved.set(index, new ArrayList<>());
            }
        }

        return resolved;
    }

    // 좌석 시야 사진 목록 조회 (공개 API용)
    public List<SeatViewPhotoDto> getSeatViewPhotos(String stadium, String section, int limit) {
        return seatViewService.getPublicSeatViews(stadium, section, limit);
    }

    private DiaryType resolveDiaryType(String typeName, DiaryType fallback) {
        if (!StringUtils.hasText(typeName)) {
            return fallback;
        }
        try {
            return DiaryType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid diary type: " + typeName, e);
        }
    }

    private DiaryEmoji resolveMood(String emojiName, DiaryType diaryType) {
        if (diaryType == DiaryType.SCHEDULED && !StringUtils.hasText(emojiName)) {
            return DEFAULT_SCHEDULED_MOOD;
        }
        return DiaryEmoji.fromKoreanName(emojiName);
    }

    private DiaryWinning resolveWinning(DiaryType diaryType, String winningName) {
        if (diaryType == DiaryType.SCHEDULED) {
            return null;
        }
        if (!StringUtils.hasText(winningName)) {
            throw new WinningNameNotFoundException();
        }
        try {
            return DiaryWinning.valueOf(winningName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new WinningNameNotFoundException();
        }
    }

    private String buildTeamLabel(GameEntity game) {
        String homeTeamKorean = BaseballConstants.getTeamKoreanName(game.getHomeTeam());
        String awayTeamKorean = BaseballConstants.getTeamKoreanName(game.getAwayTeam());
        return homeTeamKorean + " vs " + awayTeamKorean;
    }

    private void applyTicketVerification(
            BegaDiary diary,
            GameEntity game,
            LocalDate diaryDate,
            String ticketVerificationToken) {
        if (!StringUtils.hasText(ticketVerificationToken)) {
            return;
        }

        TicketInfo ticketInfo = ticketVerificationTokenStore.peekToken(ticketVerificationToken);
        if (ticketInfo == null) {
            throw new IllegalArgumentException("유효한 티켓 인증 토큰이 없습니다.");
        }

        boolean gameMatches = Objects.equals(ticketInfo.getGameId(), game != null ? game.getId() : null);
        boolean dateMatches = Objects.equals(ticketInfo.getDate(), diaryDate != null ? diaryDate.toString() : null);
        boolean stadiumMatches = !StringUtils.hasText(ticketInfo.getStadium())
                || Objects.equals(ticketInfo.getStadium(), game != null ? game.getStadium() : diary.getStadium());

        if (!gameMatches || !dateMatches || !stadiumMatches) {
            throw new IllegalArgumentException("티켓 인증 정보가 선택한 경기와 일치하지 않습니다.");
        }

        TicketInfo consumed = ticketVerificationTokenStore.consumeToken(ticketVerificationToken);
        if (consumed == null) {
            throw new IllegalArgumentException("이미 사용했거나 만료된 티켓 인증 토큰입니다.");
        }

        diary.markTicketVerified(LocalDateTime.now());
    }

    private boolean hasTicketIdentityChanged(BegaDiary existingDiary, GameEntity nextGame, String nextStadium) {
        Long existingGameId = existingDiary.getGame() != null ? existingDiary.getGame().getId() : null;
        Long nextGameId = nextGame != null ? nextGame.getId() : null;
        if (!Objects.equals(existingGameId, nextGameId)) {
            return true;
        }
        return !Objects.equals(existingDiary.getStadium(), nextStadium);
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
