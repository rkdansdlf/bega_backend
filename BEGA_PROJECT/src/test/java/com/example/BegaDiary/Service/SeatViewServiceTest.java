package com.example.BegaDiary.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.SeatViewCandidateDto;
import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Entity.SeatViewPhoto.ClassificationLabel;
import com.example.BegaDiary.Entity.SeatViewPhoto.ModerationStatus;
import com.example.BegaDiary.Entity.SeatViewPhoto.SourceType;
import com.example.BegaDiary.Entity.SeatViewSubmissionRequest;
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.admin.dto.AdminSeatViewActionReq;
import com.example.admin.dto.AdminSeatViewDto;
import com.example.auth.entity.UserEntity;
import com.example.cheerboard.storage.service.ImageService;
import com.example.common.exception.BadRequestBusinessException;
import com.example.leaderboard.dto.SeatViewRewardDto;
import com.example.leaderboard.service.ScoringService;
import com.example.media.service.MediaLinkService;

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
    @Mock
    private MediaLinkService mediaLinkService;

    @Test
    void createDirectSubmissionPersistsPendingStandalonePhotoAndLinksOwnedAsset() {
        when(seatViewPhotoRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SeatViewPhoto photo = invocation.getArgument(0);
            setId(photo, 501L);
            return photo;
        });
        var result = seatViewService.createDirectSubmission(10L, new SeatViewSubmissionRequest(
                "media/seat-view/10/31.webp", " 잠실 ", " 205블록 ", "205", "10열", "12번",
                5, "전광판이 잘 보여요", List.of("전광판 잘 보임", "그늘")));

        var photoCaptor = org.mockito.ArgumentCaptor.forClass(SeatViewPhoto.class);
        verify(seatViewPhotoRepository).saveAndFlush(photoCaptor.capture());
        SeatViewPhoto saved = photoCaptor.getValue();
        assertThat(saved.getDiary()).isNull();
        assertThat(saved.getSourceType()).isEqualTo(SourceType.SEATMAP_UPLOAD);
        assertThat(saved.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(saved.isUserSelected()).isTrue();
        assertThat(saved.getStadium()).isEqualTo("잠실");
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getTagsJson()).isEqualTo("[\"전광판 잘 보임\",\"그늘\"]");
        verify(mediaLinkService).linkSeatViewAsset(10L, "media/seat-view/10/31.webp", 501L);
        assertThat(result.id()).isEqualTo(501L);
        assertThat(result.photoUrl()).isNull();
        assertThat(result.moderationStatus()).isEqualTo("PENDING");
        assertThat(result.tags()).containsExactly("전광판 잘 보임", "그늘");
    }

    @Test
    void createDirectSubmissionRejectsUnapprovedTagsBeforePersisting() {
        assertThatThrownBy(() -> seatViewService.createDirectSubmission(10L, new SeatViewSubmissionRequest(
                "media/seat-view/10/31.webp", "잠실", null, null, null, null,
                4, null, List.of("사용자 입력 태그"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 시야뷰 태그입니다.");

        verifyNoInteractions(seatViewPhotoRepository, mediaLinkService);
    }

    @Test
    void createDirectSubmissionRejectsInvalidRatingBeforePersisting() {
        assertThatThrownBy(() -> seatViewService.createDirectSubmission(10L, new SeatViewSubmissionRequest(
                "media/seat-view/10/31.webp", "잠실", null, null, null, null,
                6, null, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("별점은 1에서 5 사이여야 합니다.");

        verifyNoInteractions(seatViewPhotoRepository, mediaLinkService);
    }

    // --- submitSelections ---

    @Test
    void submitSelections_updatesUserSelection() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);

        when(diaryRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(diary));
        when(seatViewPhotoRepository.findByDiaryIdAndUserId(1L, 10L)).thenReturn(List.of(photo));
        when(seatViewPhotoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imageService.getDiaryImageSignedUrls(anyList(), eq(10L), eq(1L), eq(300)))
                .thenReturn(Mono.just(List.of("https://signed/path")));

        List<SeatViewCandidateDto> result = seatViewService.submitSelections(1L, 10L, List.of(100L));

        assertThat(result).hasSize(1);
        verify(seatViewPhotoRepository).save(any(SeatViewPhoto.class));
    }

    @Test
    void submitSelections_throwsWhenDiaryNotFound() {
        when(diaryRepository.findByIdAndUserId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatViewService.submitSelections(99L, 10L, List.of()))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("해당 다이어리를 찾을 수 없습니다.");
    }

    @Test
    void submitSelections_treatsNonOwnedDiaryAsNotFound() {
        when(diaryRepository.findByIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatViewService.submitSelections(1L, 999L, List.of()))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("해당 다이어리를 찾을 수 없습니다.");
    }

    @Test
    void submitSelections_throwsWhenNoSeatMetadata() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", null, null, null);
        when(diaryRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> seatViewService.submitSelections(1L, 10L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("좌석 정보");
    }

    @Test
    void createCandidates_treatsNonOwnedDiaryAsNotFoundBeforeSave() {
        when(diaryRepository.findByIdAndUserId(1L, 999L)).thenReturn(Optional.empty());
        MockMultipartFile image = imageFile();

        assertThatThrownBy(() -> seatViewService.createCandidates(
                1L,
                999L,
                List.of(image),
                List.of("diary/999/1/test.webp"),
                List.of(SourceType.DIARY_UPLOAD)))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("해당 다이어리를 찾을 수 없습니다.");

        verifyNoInteractions(seatViewPhotoRepository, seatViewClassificationService);
    }

    @Test
    void createCandidatesFromStoragePaths_treatsNonOwnedDiaryAsNotFoundBeforeDownload() {
        when(diaryRepository.findByIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatViewService.createCandidatesFromStoragePaths(
                1L,
                999L,
                List.of("diary/999/1/test.webp"),
                List.of(SourceType.DIARY_UPLOAD)))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("해당 다이어리를 찾을 수 없습니다.");

        verifyNoInteractions(imageService, seatViewPhotoRepository, seatViewClassificationService);
    }

    @Test
    void createCandidatesFromStoragePaths_rejectsCrossOwnerStoragePathBeforeDownload() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        when(diaryRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(diary));
        when(imageService.normalizeDiaryStoragePathsForWrite(
                List.of("diary/999/1/test.webp"),
                10L,
                1L,
                true))
                .thenThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요."));

        assertThatThrownBy(() -> seatViewService.createCandidatesFromStoragePaths(
                1L,
                10L,
                List.of("diary/999/1/test.webp"),
                List.of(SourceType.DIARY_UPLOAD)))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("업로드를 다시 완료");

        verify(imageService, never()).downloadDiaryImageBytesForUser(any(), any(), any());
        verifyNoInteractions(seatViewPhotoRepository, seatViewClassificationService);
    }

    // --- getAdminSeatView ---

    @Test
    void getAdminSeatViewsPassesBoundedPageToRepository() {
        var requestedPage = PageRequest.of(1, 20);
        when(seatViewPhotoRepository.findForAdmin(
                null, "잠실", null, null, null, ModerationStatus.PENDING, requestedPage))
                .thenReturn(new PageImpl<>(List.of(), requestedPage, 25));

        var page = seatViewService.getAdminSeatViews(null, "잠실", null, null, null, 1, 20);

        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getTotalElements()).isEqualTo(25);
        verify(seatViewPhotoRepository).findForAdmin(
                null, "잠실", null, null, null, ModerationStatus.PENDING, requestedPage);
    }

    @Test
    void getAdminSeatViewsRejectsOversizedPageBeforeQuerying() {
        assertThatThrownBy(() -> seatViewService.getAdminSeatViews(null, null, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1에서 100");

        verifyNoInteractions(seatViewPhotoRepository);
    }

    @Test
    void getAdminSeatView_returnsDto() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);
        when(seatViewPhotoRepository.findDetailById(100L)).thenReturn(Optional.of(photo));
        when(imageService.getDiaryImageSignedUrls(anyList(), eq(10L), eq(1L), eq(300)))
                .thenReturn(Mono.just(List.of("https://signed/url")));

        AdminSeatViewDto result = seatViewService.getAdminSeatView(100L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStadium()).isEqualTo("잠실");
        assertThat(result.getPhotoUrl()).isEqualTo("https://signed/url");
    }

    @Test
    void getOwnerPreviewUrlRequiresOwnerAndUsesShortLivedSignedUrlForPendingPhoto() {
        SeatViewPhoto photo = SeatViewPhoto.builder()
                .userId(10L)
                .storagePath("media/seat-view/10/31.webp")
                .sourceType(SourceType.SEATMAP_UPLOAD)
                .moderationStatus(ModerationStatus.PENDING)
                .build();
        setId(photo, 501L);
        when(seatViewPhotoRepository.findDetailById(501L)).thenReturn(Optional.of(photo));
        when(imageService.getSeatViewImageSignedUrls(
                List.of("media/seat-view/10/31.webp"), 10L, 501L, 300))
                .thenReturn(Mono.just(List.of("https://signed/short-preview")));

        assertThat(seatViewService.getOwnerPreviewUrl(10L, 501L)).isEqualTo("https://signed/short-preview");
        assertThatThrownBy(() -> seatViewService.getOwnerPreviewUrl(11L, 501L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(imageService).getSeatViewImageSignedUrls(
                List.of("media/seat-view/10/31.webp"), 10L, 501L, 300);
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
    void reviewSeatViewRejectsOversizedMemoBeforeReadingOrChangingPhoto() {
        AdminSeatViewActionReq req = new AdminSeatViewActionReq(
                "SEAT_VIEW", "APPROVED", "m".repeat(1001));

        assertThatThrownBy(() -> seatViewService.reviewSeatView(100L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시야뷰 검수 메모는 1000자 이하여야 합니다.");

        verifyNoInteractions(seatViewPhotoRepository, scoringService);
    }

    @Test
    void reviewSeatView_approvesAndGrantsReward() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        diary.markTicketVerified(java.time.LocalDateTime.now()); // ticketVerified = true
        SeatViewPhoto photo = buildPhoto(100L, diary, 10L, SourceType.DIARY_UPLOAD);

        when(seatViewPhotoRepository.findDetailById(100L)).thenReturn(Optional.of(photo));
        when(seatViewPhotoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imageService.getDiaryImageSignedUrls(anyList(), eq(10L), eq(1L), eq(300)))
                .thenReturn(Mono.just(List.of("https://signed/url")));
        when(seatViewPhotoRepository.findApprovedByDiaryId(eq(1L), eq(ModerationStatus.APPROVED), eq(ClassificationLabel.SEAT_VIEW)))
                .thenReturn(List.of(photo));
        when(scoringService.processSeatViewReward(10L, 1L, "잠실"))
                .thenReturn(SeatViewRewardDto.builder().build());

        String adminMemo = "m".repeat(1000);
        AdminSeatViewActionReq req = new AdminSeatViewActionReq("SEAT_VIEW", "APPROVED", adminMemo);
        AdminSeatViewDto result = seatViewService.reviewSeatView(100L, 1L, req);

        assertThat(result).isNotNull();
        assertThat(result.getModerationStatus()).isEqualTo("APPROVED");
        assertThat(result.getAdminLabel()).isEqualTo("SEAT_VIEW");
        assertThat(result.getAdminMemo()).isEqualTo(adminMemo);
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

    @Test
    void publicGalleryDoesNotFallbackToUnconsentedLegacyDiaryPhotos() {
        when(seatViewPhotoRepository.findApprovedPublicSeatViews(
                eq("잠실"), eq("1루"), eq(ModerationStatus.APPROVED), eq(ClassificationLabel.SEAT_VIEW), any()))
                .thenReturn(List.of());

        assertThat(seatViewService.getPublicSeatViews("잠실", "1루", 10)).isEmpty();

        verifyNoInteractions(diaryRepository, imageService);
    }

    @Test
    void publicGallerySignsOnlyConsentedApprovedDiarySeatViewsAndAllowsFewerResults() {
        BegaDiary diary = buildDiary(1L, 10L, "잠실", "1루", "A", "10");
        SeatViewPhoto approved = publicPhoto(diary, SourceType.DIARY_UPLOAD, true,
                ModerationStatus.APPROVED, ClassificationLabel.SEAT_VIEW, "approved.webp");
        List<SeatViewPhoto> candidates = List.of(
                publicPhoto(diary, SourceType.DIARY_UPLOAD, false, ModerationStatus.APPROVED,
                        ClassificationLabel.SEAT_VIEW, "not-selected.webp"),
                publicPhoto(diary, SourceType.DIARY_UPLOAD, true, ModerationStatus.PENDING,
                        ClassificationLabel.SEAT_VIEW, "unreviewed.webp"),
                publicPhoto(diary, SourceType.TICKET_SCAN, true, ModerationStatus.APPROVED,
                        ClassificationLabel.SEAT_VIEW, "ticket-scan.webp"),
                publicPhoto(diary, SourceType.DIARY_UPLOAD, true, ModerationStatus.APPROVED,
                        ClassificationLabel.TICKET, "ticket-label.webp"), approved);
        when(seatViewPhotoRepository.findApprovedPublicSeatViews(
                eq("잠실"), eq("1루"), eq(ModerationStatus.APPROVED), eq(ClassificationLabel.SEAT_VIEW), any()))
                .thenReturn(candidates);
        when(imageService.getDiaryImageSignedUrls(List.of("approved.webp"), 10L, 1L))
                .thenReturn(Mono.just(List.of("https://example.invalid/approved")));

        var result = seatViewService.getPublicSeatViews("잠실", "1루", 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPhotoUrl()).isEqualTo("https://example.invalid/approved");
        verify(imageService).getDiaryImageSignedUrls(List.of("approved.webp"), 10L, 1L);
        org.mockito.Mockito.verifyNoMoreInteractions(imageService);
        verifyNoInteractions(diaryRepository);
    }

    @Test
    void publicGalleryReturnsApprovedStandaloneSeatViewsWithDirectMetadata() {
        SeatViewPhoto approved = SeatViewPhoto.builder()
                .userId(10L)
                .storagePath("media/seat-view/10/31.webp")
                .sourceType(SourceType.SEATMAP_UPLOAD)
                .stadium("잠실")
                .section("205블록")
                .block("205")
                .seatRow("10열")
                .seatNumber("12번")
                .rating(5)
                .commentText("전광판이 잘 보여요")
                .tagsJson("[\"전광판 잘 보임\"]")
                .userSelected(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .adminLabel(ClassificationLabel.SEAT_VIEW)
                .build();
        setId(approved, 501L);
        when(seatViewPhotoRepository.findApprovedPublicSeatViews(
                eq("잠실"), eq("205블록"), eq(ModerationStatus.APPROVED), eq(ClassificationLabel.SEAT_VIEW), any()))
                .thenReturn(List.of(approved));
        when(imageService.getSeatViewImageSignedUrls(
                List.of("media/seat-view/10/31.webp"), 10L, 501L))
                .thenReturn(Mono.just(List.of("https://signed/seat-view")));

        var result = seatViewService.getPublicSeatViews("잠실", "205블록", 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPhotoUrl()).isEqualTo("https://signed/seat-view");
        assertThat(result.getFirst().getStadium()).isEqualTo("잠실");
        assertThat(result.getFirst().getRating()).isEqualTo(5);
        assertThat(result.getFirst().getTags()).containsExactly("전광판 잘 보임");
    }

    @Test
    void reviewingStandaloneSeatViewDoesNotGrantDiaryReward() {
        SeatViewPhoto photo = SeatViewPhoto.builder()
                .userId(10L)
                .storagePath("media/seat-view/10/31.webp")
                .sourceType(SourceType.SEATMAP_UPLOAD)
                .stadium("잠실")
                .rating(5)
                .userSelected(true)
                .moderationStatus(ModerationStatus.PENDING)
                .build();
        setId(photo, 501L);
        when(seatViewPhotoRepository.findDetailById(501L)).thenReturn(Optional.of(photo));
        when(seatViewPhotoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageService.getSeatViewImageSignedUrls(
                List.of("media/seat-view/10/31.webp"), 10L, 501L, 300))
                .thenReturn(Mono.just(List.of("https://signed/seat-view")));

        var result = seatViewService.reviewSeatView(
                501L,
                1L,
                new AdminSeatViewActionReq("SEAT_VIEW", "APPROVED", null));

        assertThat(result.getModerationStatus()).isEqualTo("APPROVED");
        verifyNoInteractions(scoringService);
    }

    private SeatViewPhoto publicPhoto(BegaDiary diary, SourceType source, boolean selected,
            ModerationStatus status, ClassificationLabel label, String path) {
        return SeatViewPhoto.builder().diary(diary).userId(10L).storagePath(path)
                .sourceType(source).userSelected(selected).moderationStatus(status).adminLabel(label).build();
    }

    private void setId(SeatViewPhoto photo, Long id) {
        try {
            java.lang.reflect.Field idField = SeatViewPhoto.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(photo, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("images", "seat.webp", "image/webp", "image".getBytes());
    }
}
