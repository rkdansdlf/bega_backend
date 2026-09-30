package com.example.BegaDiary.Service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.leaderboard.entity.Achievement;
import com.example.leaderboard.service.AchievementService;

import lombok.RequiredArgsConstructor;

/**
 * 다이어리 저장과 직관 출석 업적 부여를 하나의 트랜잭션으로 묶는다.
 *
 * <p>예전에는 컨트롤러가 {@code save} 커밋 뒤에 count/업적 저장을 별도 트랜잭션으로 호출했다.
 * 업적 저장이 실패하면 다이어리는 이미 커밋됐는데 응답은 500이 되어, 클라이언트가 재시도하면
 * "이미 작성된 다이어리" 오류를 만났다. 이제 업적 단계가 실패하면 다이어리 저장도 함께 롤백된다.
 * 응답에 즉시 {@code unlockedAchievements} 를 내려야 하므로 after-commit 이벤트가 아니라
 * 같은 트랜잭션을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class DiaryCreationService {

    private final BegaDiaryService diaryService;
    private final BegaDiaryRepository diaryRepository;
    private final AchievementService achievementService;

    public record DiaryCreateResult(BegaDiary diary, List<Achievement> unlockedAchievements) {
    }

    @Transactional
    public DiaryCreateResult create(Long userId, DiaryRequestDto requestDto) {
        BegaDiary savedDiary = diaryService.save(userId, requestDto);
        int totalAttendances = diaryRepository.countByUserIdAndType(userId, BegaDiary.DiaryType.ATTENDED);
        List<Achievement> unlocked = achievementService.checkAttendanceAchievements(userId, totalAttendances);
        return new DiaryCreateResult(savedDiary, unlocked);
    }
}
