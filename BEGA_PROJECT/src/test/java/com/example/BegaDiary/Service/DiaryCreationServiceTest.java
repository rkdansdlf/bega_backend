package com.example.BegaDiary.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.leaderboard.entity.Achievement;
import com.example.leaderboard.service.AchievementService;

@ExtendWith(MockitoExtension.class)
class DiaryCreationServiceTest {

    @Mock
    private BegaDiaryService diaryService;

    @Mock
    private BegaDiaryRepository diaryRepository;

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private DiaryCreationService diaryCreationService;

    private static BegaDiary diary(BegaDiary.DiaryType type) {
        return BegaDiary.builder()
                .user(UserEntity.builder().id(1L).build())
                .diaryDate(java.time.LocalDate.of(2026, 3, 23))
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(type)
                .build();
    }

    @Test
    @DisplayName("저장 → 직관 수 집계 → 업적 부여 순으로 실행하고 결과를 함께 돌려준다")
    void create_savesThenCountsThenAwards() {
        BegaDiary saved = diary(BegaDiary.DiaryType.ATTENDED);
        Achievement achievement = Achievement.builder().id(1L).code("FIRST_ATTENDANCE").nameKo("첫 직관").build();
        DiaryRequestDto request = new DiaryRequestDto();
        when(diaryService.save(1L, request)).thenReturn(saved);
        when(diaryRepository.countByUserIdAndType(1L, BegaDiary.DiaryType.ATTENDED)).thenReturn(1);
        when(achievementService.checkAttendanceAchievements(1L, 1)).thenReturn(List.of(achievement));

        DiaryCreationService.DiaryCreateResult result = diaryCreationService.create(1L, request);

        assertSame(saved, result.diary());
        assertEquals(List.of(achievement), result.unlockedAchievements());
        InOrder order = inOrder(diaryService, diaryRepository, achievementService);
        order.verify(diaryService).save(1L, request);
        order.verify(diaryRepository).countByUserIdAndType(1L, BegaDiary.DiaryType.ATTENDED);
        order.verify(achievementService).checkAttendanceAchievements(1L, 1);
    }

    @Test
    @DisplayName("예정(SCHEDULED) 기록은 직관 출석 수에 포함하지 않는다 (ATTENDED만 집계)")
    void create_scheduledDiaryDoesNotInflateAttendanceCount() {
        DiaryRequestDto request = new DiaryRequestDto();
        when(diaryService.save(1L, request)).thenReturn(diary(BegaDiary.DiaryType.SCHEDULED));
        when(diaryRepository.countByUserIdAndType(1L, BegaDiary.DiaryType.ATTENDED)).thenReturn(0);
        when(achievementService.checkAttendanceAchievements(1L, 0)).thenReturn(List.of());

        DiaryCreationService.DiaryCreateResult result = diaryCreationService.create(1L, request);

        assertEquals(List.of(), result.unlockedAchievements());
        verify(achievementService).checkAttendanceAchievements(1L, 0);
    }

    @Test
    @DisplayName("업적 단계 실패는 삼키지 않고 전파한다 — 같은 트랜잭션이라 다이어리 저장도 롤백된다")
    void create_achievementFailurePropagates() {
        DiaryRequestDto request = new DiaryRequestDto();
        when(diaryService.save(1L, request)).thenReturn(diary(BegaDiary.DiaryType.ATTENDED));
        when(diaryRepository.countByUserIdAndType(anyLong(), any())).thenReturn(1);
        when(achievementService.checkAttendanceAchievements(1L, 1)).thenThrow(new IllegalStateException("achievement save failed"));

        assertThrows(IllegalStateException.class, () -> diaryCreationService.create(1L, request));
    }

    @Test
    @DisplayName("create는 단일 쓰기 트랜잭션이다 (읽기 전용이나 트랜잭션 없음으로 바뀌면 원자성이 깨진다)")
    void create_isASingleWriteTransaction() throws Exception {
        Transactional tx = DiaryCreationService.class
                .getMethod("create", Long.class, DiaryRequestDto.class)
                .getAnnotation(Transactional.class);

        assertNotNull(tx);
        assertEquals(false, tx.readOnly());
    }
}
