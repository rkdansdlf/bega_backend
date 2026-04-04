package com.example.BegaDiary.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.SeatViewCandidateDto;
import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Entity.SeatViewPhoto.ClassificationLabel;
import com.example.BegaDiary.Entity.SeatViewPhoto.ModerationStatus;
import com.example.BegaDiary.Entity.SeatViewPhoto.SourceType;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.admin.dto.AdminSeatViewActionReq;
import com.example.admin.dto.AdminSeatViewDto;
import com.example.auth.entity.UserEntity;
import com.example.cheerboard.storage.service.ImageService;
import com.example.leaderboard.dto.SeatViewRewardDto;
import com.example.leaderboard.service.ScoringService;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SeatViewServiceTest {

    @InjectMocks
    private SeatViewService seatViewService;

    @Mock
    private SeatViewPhotoRepository seatViewPhotoRepository;
    @Mock
    private BegaDiaryRepository diaryRepository;
    @Mock
    private ImageService imageService;
    @Mock
    private SeatViewClassificationService seatViewClassificationService;
    @Mock
    private ScoringService scoringService;

    // --- submitSelections ---

    @Test
    void submitSelections_updatesUserSelection() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);

        when(diaryRepository.findById(1L)).thenReturn(Optional.of(diary));
        when(seatViewPhotoRepository.findByDiaryIdAndUserId(1L, 10L)).thenReturn(List.of(photo));
        when(seatViewPhotoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imageService.getDiaryImageSignedUrls(anyList())).thenReturn(Mono.just(List.of("https://signed/path")));

        List<SeatViewCandidateDto> result = seatViewService.submitSelections(1L, 10L, List.of(100L));

        assertThat(result).hasSize(1);
        verify(seatViewPhotoRepository).save(any(SeatViewPhoto.class));
    }

    @Test
    void submitSelections_throwsWhenDiaryNotFound() {
        when(diaryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatViewService.submitSelections(99L, 10L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다이어리");
    }

    @Test
    void submitSelections_throwsWhenNotOwner() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        when(diaryRepository.findById(1L)).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> seatViewService.submitSelections(1L, 999L, List.of()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submitSelections_throwsWhenNoSeatMetadata() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", null, null, null);
        when(diaryRepository.findById(1L)).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> seatViewService.submitSelections(1L, 10L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("좌석 정보");
    }

    // --- getAdminSeatView ---

    @Test
    void getAdminSeatView_returnsDto() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);
        when(seatViewPhotoRepository.findDetailById(100L)).thenReturn(Optional.of(photo));
        when(imageService.getDiaryImageSignedUrls(anyList())).thenReturn(Mono.just(List.of("https://signed/url")));

        AdminSeatViewDto result = seatViewService.getAdminSeatView(100L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStadium()).isEqualTo("잠실");
    }

    @Test
    void getAdminSeatView_throwsWhenNotFound() {
        when(seatViewPhotoRepository.findDetailById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatViewService.getAdminSeatView(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시야뷰 후보");
    }

    // --- reviewSeatView ---

    @Test
    void reviewSeatView_approvesAndGrantsReward() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        diary.markTicketVerified(java.time.LocalDateTime.now()); // ticketVerified = true
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);

        when(seatViewPhotoRepository.findDetailById(100L)).thenReturn(Optional.of(photo));
        when(seatViewPhotoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imageService.getDiaryImageSignedUrls(anyList())).thenReturn(Mono.just(List.of("https://signed/url")));
        when(seatViewPhotoRepository.findApprovedByDiaryId(eq(1L), eq(ModerationStatus.APPROVED), eq(ClassificationLabel.SEAT_VIEW)))
                .thenReturn(List.of(photo));
        when(scoringService.processSeatViewReward(10L, 1L, "잠실"))
                .thenReturn(SeatViewRewardDto.builder().build());

        AdminSeatViewActionReq req = new AdminSeatViewActionReq("SEAT_VIEW", "APPROVED", "좋은 시야");
        AdminSeatViewDto result = seatViewService.reviewSeatView(100L, 1L, req);

        assertThat(result).isNotNull();
        assertThat(result.getModerationStatus()).isEqualTo("APPROVED");
        assertThat(result.getAdminLabel()).isEqualTo("SEAT_VIEW");
    }

    @Test
    void reviewSeatView_throwsWhenApprovedWithNonSeatViewLabel() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);
        when(seatViewPhotoRepository.findDetailById(100L)).thenReturn(Optional.of(photo));

        AdminSeatViewActionReq req = new AdminSeatViewActionReq("OTHER", "APPROVED", "잘못됨");

        assertThatThrownBy(() -> seatViewService.reviewSeatView(100L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEAT_VIEW 라벨");
    }

    @Test
    void reviewSeatView_throwsWhenRejectedWithSeatViewLabel() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);
        when(seatViewPhotoRepository.findDetailById(100L)).thenReturn(Optional.of(photo));

        AdminSeatViewActionReq req = new AdminSeatViewActionReq("SEAT_VIEW", "REJECTED", "거절");

        assertThatThrownBy(() -> seatViewService.reviewSeatView(100L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("승인 상태와 함께");
    }

    // --- processDiaryRewardIfEligible ---

    @Test
    void processDiaryRewardIfEligible_returnsNullWhenDiaryNull() {
        SeatViewRewardDto result = seatViewService.processDiaryRewardIfEligible(null);
        assertThat(result).isNull();
    }

    @Test
    void processDiaryRewardIfEligible_returnsNullWhenNotTicketVerified() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        // ticketVerified defaults to false

        SeatViewRewardDto result = seatViewService.processDiaryRewardIfEligible(diary);
        assertThat(result).isNull();
    }

    @Test
    void processDiaryRewardIfEligible_returnsNullWhenNoApprovedPhotos() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        diary.markTicketVerified(java.time.LocalDateTime.now());
        when(seatViewPhotoRepository.findApprovedByDiaryId(eq(1L), eq(ModerationStatus.APPROVED), eq(ClassificationLabel.SEAT_VIEW)))
                .thenReturn(List.of());

        SeatViewRewardDto result = seatViewService.processDiaryRewardIfEligible(diary);
        assertThat(result).isNull();
    }

    // --- deleteByDiaryId ---

    @Test
    void deleteByDiaryId_delegatesToRepository() {
        seatViewService.deleteByDiaryId(1L);
        verify(seatViewPhotoRepository).deleteByDiary_Id(1L);
    }

    // --- helpers ---

    private BegaDiary buildDiary(Long id, Long userId, String stadium, String section, String block, String seatRow) {
        UserEntity user = UserEntity.builder().build();
        // Set id via reflection since UserEntity.id is likely private
        try {
            java.lang.reflect.Field idField = UserEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        BegaDiary diary = BegaDiary.builder()
                .diaryDate(LocalDate.of(2026, 4, 1))
                .user(user)
                .stadium(stadium)
                .section(section)
                .block(block)
                .seatRow(seatRow)
                .team("KIA")
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(BegaDiary.DiaryWinning.WIN)
                .build();

        try {
            java.lang.reflect.Field idField = BegaDiary.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(diary, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return diary;
    }

    private SeatViewPhoto buildPhoto(Long id, BegaDiary diary, Long userId, SourceType sourceType) {
        SeatViewPhoto photo = SeatViewPhoto.builder()
                .diary(diary)
                .userId(userId)
                .storagePath("diary/photos/test.jpg")
                .sourceType(sourceType)
                .userSelected(false)
                .rewardGranted(false)
                .build();

        try {
            java.lang.reflect.Field idField = SeatViewPhoto.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(photo, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return photo;
    }
}
