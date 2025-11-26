package com.example.BegaDiary.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.example.BegaDiary.Entity.BegaGame;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Entity.DiaryStatisticsDto;
import com.example.BegaDiary.Exception.DiaryAlreadyExistsException;
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Exception.GameNotFoundException;
import com.example.BegaDiary.Exception.ImageProcessingException;
import com.example.BegaDiary.Exception.WinningNameNotFoundException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.BegaGameRepository;
import com.example.BegaDiary.Utils.BaseballConstants;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.BegaDiary.Utils.BaseballConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BegaDiaryService {
    
    private final BegaDiaryRepository diaryRepository;
    private final BegaGameRepository gameRepository;
    private final BegaGameService gameService;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final CheerPostRepo cheerPostRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    
    // 전체 다이어리 조회
    public List<DiaryResponseDto> getAllDiaries(Long userId) {
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
                            log.error("다이어리 이미지 Signed URL 생성 실패: diaryId={}, error={}", 
                                diary.getId(), e.getMessage());
                            signedUrls = new ArrayList<>();
                        }
                    }
                    
                    return DiaryResponseDto.from(diary, signedUrls);
                })
                .collect(Collectors.toList());
    }
    
    // 특정 다이어리 조회
    public DiaryResponseDto getDiaryById(Long id) {
        BegaDiary diary = this.diaryRepository.findById(id)
            .orElseThrow(() -> new DiaryNotFoundException(id));
        
        List<String> signedUrls = imageService
                .getDiaryImageSignedUrls(diary.getPhotoUrls())
                .block();
        
        return DiaryResponseDto.from(diary, signedUrls);
    }
    
    // 다이어리 저장
    @Transactional
    public BegaDiary save(Long userId, DiaryRequestDto requestDto) {
        // 1. 날짜 파싱
        LocalDate diaryDate = LocalDate.parse(requestDto.getDate());
        
        UserEntity user = this.userRepository.findById(userId).get();
        
        // 2. 중복 체크
        if (diaryRepository.existsByUserAndDiaryDate(user, diaryDate)) {
            throw new DiaryAlreadyExistsException(
                diaryDate + "에 이미 다이어리가 작성되어 있습니다."
            );
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
        if(requestDto.getGameId() == null) {
        	throw new GameNotFoundException();
        }
        
        BegaGame game = null;
        String team = "";
        String stadium = "";
        if(requestDto.getGameId() != null) {
    		game = gameService.getGameById(requestDto.getGameId());

            String homeTeamKorean = BaseballConstants.getTeamKoreanName(game.getHomeTeam());
            String awayTeamKorean = BaseballConstants.getTeamKoreanName(game.getAwayTeam());
            team = homeTeamKorean + " vs " + awayTeamKorean;

            stadium = BaseballConstants.getFullStadiumName(game.getStadium());
    	}
    	
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
            .build();
        
        // 5. DB 저장
        return diaryRepository.save(diary);
    }
    
    @Async
    @Transactional
    public CompletableFuture<List<String>> addImages(Long diaryId, Long userId, List<MultipartFile> images) {
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
            List<String> allPaths = new ArrayList<>(diary.getPhotoUrls());
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
        BegaDiary diary = this.diaryRepository.findById(id)
            .orElseThrow(() -> new DiaryNotFoundException(id));
        
        DiaryEmoji mood = DiaryEmoji.fromKoreanName(requestDto.getEmojiName());
        
        diary.updateDiary(
            requestDto.getMemo(),
            mood,
            requestDto.getPhotos()
        );
        
        return diary;
    }
    
    // 다이어리 삭제
    @Transactional
    public void delete(Long id) {
        BegaDiary diary = this.diaryRepository.findById(id)
            .orElseThrow(() -> new DiaryNotFoundException(id));
        
        if(diary.getPhotoUrls() != null && !diary.getPhotoUrls().isEmpty()) {
        	try {
                imageService.deleteDiaryImages(diary.getPhotoUrls()).block();
            } catch (Exception e) {
                System.out.printf("이미지 삭제 실패 (다이어리는 삭제됨): diaryId={}", id, e);
            }
        }
        
        this.diaryRepository.delete(diary);
    }
    
    public DiaryStatisticsDto getStatistics(Long userId) {
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

        Map<String, Long> emojiCounts = diaries.stream()
        .collect(Collectors.groupingBy(
            diary -> diary.getMood().getKoreanName(),
            Collectors.counting()
        ));
        
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
            .build();
    }
}