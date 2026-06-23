package com.example.BegaDiary.Service;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Exception.WinningNameNotFoundException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.kbo.entity.GameEntity;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.media.entity.MediaDomain;
import com.example.media.service.MediaLinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BegaDiaryService tests")
class BegaDiaryServiceTest {

    @Mock
    private BegaDiaryRepository diaryRepository;

    @Mock
    private BegaGameService gameService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private CheerPostRepo cheerPostRepository;

    @Mock
    private PartyApplicationRepository partyApplicationRepository;

    @Mock
    private TicketVerificationTokenStore ticketVerificationTokenStore;

    @Mock
    private SeatViewService seatViewService;

    @Mock
    private MediaLinkService mediaLinkService;

    @InjectMocks
    private BegaDiaryService begaDiaryService;

    @Test
    @DisplayName("getDiaryById rejects another user's diary")
    void getDiaryById_rejectsAnotherUsersDiary() {
        when(diaryRepository.findByIdAndUserId(100L, 77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> begaDiaryService.getDiaryById(100L, 77L))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("해당 다이어리를 찾을 수 없습니다.");

        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("getDiaryById returns owner's diary with signed image URLs")
    void getDiaryById_returnsOwnersDiary() {
        BegaDiary diary = createDiary(100L, 10L, List.of("oci://diary/object.png"));
        when(diaryRepository.findByIdAndUserId(100L, 10L)).thenReturn(Optional.of(diary));
        when(imageService.getDiaryImageSignedUrls(List.of("oci://diary/object.png"), 10L, 100L))
                .thenReturn(reactor.core.publisher.Mono.just(List.of("https://signed.example/diary.png")));

        DiaryResponseDto response = begaDiaryService.getDiaryById(100L, 10L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getPhotos()).containsExactly("https://signed.example/diary.png");
        assertThat(response.getPhotoStoragePaths()).containsExactly("oci://diary/object.png");
    }

    @Test
    @DisplayName("getAllDiaries signs diary photos with owner and diary context")
    void getAllDiaries_signsUrlsWithOwnerContext() {
        BegaDiary first = createDiary(100L, 10L, List.of("oci://diary/object-a.png", "oci://diary/object-b.png"));
        BegaDiary second = createDiary(101L, 10L, List.of("oci://diary/object-c.png"));
        when(diaryRepository.findByUserId(10L)).thenReturn(List.of(first, second));
        when(imageService.getDiaryImageSignedUrls(List.of(
                "oci://diary/object-a.png",
                "oci://diary/object-b.png"), 10L, 100L))
                .thenReturn(reactor.core.publisher.Mono.just(List.of(
                        "https://signed.example/a.png",
                        "https://signed.example/b.png")));
        when(imageService.getDiaryImageSignedUrls(List.of("oci://diary/object-c.png"), 10L, 101L))
                .thenReturn(reactor.core.publisher.Mono.just(List.of("https://signed.example/c.png")));

        List<DiaryResponseDto> response = begaDiaryService.getAllDiaries(10L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getPhotos()).containsExactly(
                "https://signed.example/a.png",
                "https://signed.example/b.png");
        assertThat(response.get(1).getPhotos()).containsExactly("https://signed.example/c.png");
        verify(imageService).getDiaryImageSignedUrls(List.of(
                "oci://diary/object-a.png",
                "oci://diary/object-b.png"), 10L, 100L);
        verify(imageService).getDiaryImageSignedUrls(List.of("oci://diary/object-c.png"), 10L, 101L);
    }

    @Test
    @DisplayName("save stores scheduled diary without winning or attended-only fields")
    void save_storesScheduledDiaryWithoutWinning() {
        Long userId = 10L;
        UserEntity owner = owner(userId);
        GameEntity game = game(77L);
        DiaryRequestDto requestDto = new DiaryRequestDto();
        requestDto.setDate("2026-04-01");
        requestDto.setType("scheduled");
        requestDto.setGameId(77L);
        requestDto.setWinningName(null);
        requestDto.setMemo("경기 전 메모");
        requestDto.setPhotos(List.of("media/diary/10/ignored.webp"));
        requestDto.setSection("1루");
        requestDto.setSeatRow("A");
        requestDto.setTicketVerificationToken("ticket-token");

        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(diaryRepository.existsByUserAndDiaryDate(owner, LocalDate.of(2026, 4, 1))).thenReturn(false);
        when(gameService.getGameById(77L)).thenReturn(game);
        when(diaryRepository.save(any(BegaDiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BegaDiary saved = begaDiaryService.save(userId, requestDto);

        assertThat(saved.getType()).isEqualTo(BegaDiary.DiaryType.SCHEDULED);
        assertThat(saved.getWinning()).isNull();
        assertThat(saved.getMood()).isEqualTo(BegaDiary.DiaryEmoji.HAPPY);
        assertThat(saved.getPhotoUrls()).isEmpty();
        assertThat(saved.getSection()).isNull();
        assertThat(saved.getSeatRow()).isNull();
        assertThat(saved.isTicketVerified()).isFalse();
        verifyNoInteractions(imageService, ticketVerificationTokenStore, seatViewService);
    }

    @Test
    @DisplayName("save rejects attended diary without winning")
    void save_rejectsAttendedDiaryWithoutWinning() {
        Long userId = 10L;
        UserEntity owner = owner(userId);
        DiaryRequestDto requestDto = new DiaryRequestDto();
        requestDto.setDate("2026-04-01");
        requestDto.setType("attended");
        requestDto.setGameId(77L);
        requestDto.setEmojiName("즐거움");

        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(diaryRepository.existsByUserAndDiaryDate(owner, LocalDate.of(2026, 4, 1))).thenReturn(false);

        assertThatThrownBy(() -> begaDiaryService.save(userId, requestDto))
                .isInstanceOf(WinningNameNotFoundException.class);

        verifyNoInteractions(gameService, imageService, mediaLinkService, ticketVerificationTokenStore, seatViewService);
    }

    @Test
    @DisplayName("update stores canonical diary keys instead of signed URLs")
    void update_normalizesSignedUrlsToStoragePaths() {
        BegaDiary diary = createDiary(100L, 10L, List.of("diary/10/100/existing.webp"));
        when(diaryRepository.findByIdAndUserId(100L, 10L)).thenReturn(Optional.of(diary));
        when(imageService.normalizeDiaryStoragePathsForWrite(List.of(
                "https://signed.example.com/diary/10/100/existing.webp?sig=abc",
                "kbo-bucket/media/diary/10/new.webp"), 10L, 100L, true))
                .thenReturn(List.of("diary/10/100/existing.webp", "media/diary/10/new.webp"));

        DiaryRequestDto requestDto = new DiaryRequestDto();
        requestDto.setMemo("수정된 메모");
        requestDto.setEmojiName("즐거움");
        requestDto.setWinningName("WIN");
        requestDto.setPhotos(List.of(
                "https://signed.example.com/diary/10/100/existing.webp?sig=abc",
                "kbo-bucket/media/diary/10/new.webp"));
        requestDto.setSection("1루");
        requestDto.setBlock("101");
        requestDto.setSeatRow("A");
        requestDto.setSeatNumber("1");

        BegaDiary updated = begaDiaryService.update(100L, 10L, requestDto);

        assertThat(updated.getPhotoUrls()).containsExactly(
                "diary/10/100/existing.webp",
                "media/diary/10/new.webp");
        verify(imageService).normalizeDiaryStoragePathsForWrite(requestDto.getPhotos(), 10L, 100L, true);
    }

    @Test
    @DisplayName("update can convert scheduled diary to attended with winning")
    void update_convertsScheduledDiaryToAttended() {
        BegaDiary diary = createDiary(
                100L,
                10L,
                List.of(),
                BegaDiary.DiaryType.SCHEDULED,
                null);
        GameEntity game = game(77L);
        when(diaryRepository.findByIdAndUserId(100L, 10L)).thenReturn(Optional.of(diary));
        when(gameService.getGameById(77L)).thenReturn(game);
        when(imageService.normalizeDiaryStoragePathsForWrite(List.of("media/diary/10/new.webp"), 10L, 100L, true))
                .thenReturn(List.of("media/diary/10/new.webp"));

        DiaryRequestDto requestDto = new DiaryRequestDto();
        requestDto.setType("attended");
        requestDto.setGameId(77L);
        requestDto.setEmojiName("즐거움");
        requestDto.setWinningName("WIN");
        requestDto.setPhotos(List.of("media/diary/10/new.webp"));
        requestDto.setSection("3루");

        BegaDiary updated = begaDiaryService.update(100L, 10L, requestDto);

        assertThat(updated.getType()).isEqualTo(BegaDiary.DiaryType.ATTENDED);
        assertThat(updated.getWinning()).isEqualTo(BegaDiary.DiaryWinning.WIN);
        assertThat(updated.getPhotoUrls()).containsExactly("media/diary/10/new.webp");
        assertThat(updated.getSection()).isEqualTo("3루");
        verify(seatViewService).processDiaryRewardIfEligible(updated);
    }

    @Test
    @DisplayName("update can convert attended diary to scheduled and clear winning")
    void update_convertsAttendedDiaryToScheduled() {
        BegaDiary diary = createDiary(100L, 10L, List.of("media/diary/10/existing.webp"));
        GameEntity game = game(77L);
        when(diaryRepository.findByIdAndUserId(100L, 10L)).thenReturn(Optional.of(diary));
        when(gameService.getGameById(77L)).thenReturn(game);

        DiaryRequestDto requestDto = new DiaryRequestDto();
        requestDto.setType("scheduled");
        requestDto.setGameId(77L);
        requestDto.setWinningName("");
        requestDto.setPhotos(List.of("media/diary/10/existing.webp"));
        requestDto.setSection("1루");

        BegaDiary updated = begaDiaryService.update(100L, 10L, requestDto);

        assertThat(updated.getType()).isEqualTo(BegaDiary.DiaryType.SCHEDULED);
        assertThat(updated.getWinning()).isNull();
        assertThat(updated.getPhotoUrls()).isEmpty();
        assertThat(updated.getSection()).isNull();
        assertThat(updated.isTicketVerified()).isFalse();
        verifyNoInteractions(imageService, ticketVerificationTokenStore, seatViewService);
    }

    @Test
    @DisplayName("update treats another user's diary as not found")
    void update_rejectsAnotherUsersDiaryAsNotFound() {
        when(diaryRepository.findByIdAndUserId(100L, 77L)).thenReturn(Optional.empty());

        DiaryRequestDto requestDto = new DiaryRequestDto();

        assertThatThrownBy(() -> begaDiaryService.update(100L, 77L, requestDto))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("해당 다이어리를 찾을 수 없습니다.");

        verifyNoInteractions(imageService, mediaLinkService, seatViewService);
    }

    @Test
    @DisplayName("delete removes only the authenticated owner's diary")
    void delete_removesOwnersDiary() {
        BegaDiary diary = createDiary(100L, 10L, List.of("diary/10/100/existing.webp"));
        when(diaryRepository.findByIdAndUserId(100L, 10L)).thenReturn(Optional.of(diary));
        when(imageService.deleteDiaryImages(List.of("diary/10/100/existing.webp"), 10L, 100L)).thenReturn(Mono.empty());

        begaDiaryService.delete(100L, 10L);

        verify(imageService).deleteDiaryImages(List.of("diary/10/100/existing.webp"), 10L, 100L);
        verify(seatViewService).deleteByDiaryId(100L);
        verify(mediaLinkService).unlinkEntity(MediaDomain.DIARY, 100L);
        verify(diaryRepository).delete(diary);
    }

    @Test
    @DisplayName("delete treats another user's diary as not found")
    void delete_rejectsAnotherUsersDiaryAsNotFound() {
        when(diaryRepository.findByIdAndUserId(100L, 77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> begaDiaryService.delete(100L, 77L))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("해당 다이어리를 찾을 수 없습니다.");

        verifyNoInteractions(imageService, mediaLinkService, seatViewService);
    }

    private BegaDiary createDiary(Long diaryId, Long ownerId, List<String> photoUrls) {
        return createDiary(diaryId, ownerId, photoUrls, BegaDiary.DiaryType.ATTENDED, BegaDiary.DiaryWinning.WIN);
    }

    private BegaDiary createDiary(
            Long diaryId,
            Long ownerId,
            List<String> photoUrls,
            BegaDiary.DiaryType type,
            BegaDiary.DiaryWinning winning) {
        UserEntity owner = UserEntity.builder()
                .id(ownerId)
                .email("owner@test.com")
                .name("Owner")
                .build();

        BegaDiary diary = BegaDiary.builder()
                .diaryDate(LocalDate.of(2026, 3, 9))
                .memo("직관 기록")
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(type)
                .winning(winning)
                .photoUrls(photoUrls)
                .user(owner)
                .team("LG vs KT")
                .stadium("잠실야구장")
                .section("1루")
                .block("101")
                .seatRow("A")
                .seatNumber("1")
                .build();
        ReflectionTestUtils.setField(diary, "id", diaryId);
        return diary;
    }

    private UserEntity owner(Long ownerId) {
        return UserEntity.builder()
                .id(ownerId)
                .email("owner@test.com")
                .name("Owner")
                .build();
    }

    private GameEntity game(Long gameId) {
        GameEntity game = GameEntity.builder()
                .gameId("2026040101")
                .gameDate(LocalDate.of(2026, 4, 1))
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("KT")
                .gameStatus("SCHEDULED")
                .build();
        ReflectionTestUtils.setField(game, "id", gameId);
        return game;
    }
}
