package com.example.BegaDiary.Service;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.repository.PartyApplicationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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

    @InjectMocks
    private BegaDiaryService begaDiaryService;

    @Test
    @DisplayName("getDiaryById rejects another user's diary")
    void getDiaryById_rejectsAnotherUsersDiary() {
        BegaDiary diary = createDiary(100L, 10L, List.of("oci://signed/object.png"));
        when(diaryRepository.findById(100L)).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> begaDiaryService.getDiaryById(100L, 77L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("본인의 일기만 조회할 수 있습니다.");
    }

    @Test
    @DisplayName("getDiaryById returns owner's diary with signed image URLs")
    void getDiaryById_returnsOwnersDiary() {
        BegaDiary diary = createDiary(100L, 10L, List.of("oci://diary/object.png"));
        when(diaryRepository.findById(100L)).thenReturn(Optional.of(diary));
        when(imageService.getDiaryImageSignedUrls(List.of("oci://diary/object.png")))
                .thenReturn(reactor.core.publisher.Mono.just(List.of("https://signed.example/diary.png")));

        DiaryResponseDto response = begaDiaryService.getDiaryById(100L, 10L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getPhotos()).containsExactly("https://signed.example/diary.png");
    }

    @Test
    @DisplayName("getAllDiaries batches signed URL generation for all diary photos")
    void getAllDiaries_batchesSignedUrls() {
        BegaDiary first = createDiary(100L, 10L, List.of("oci://diary/object-a.png", "oci://diary/object-b.png"));
        BegaDiary second = createDiary(101L, 10L, List.of("oci://diary/object-c.png"));
        when(diaryRepository.findByUserId(10L)).thenReturn(List.of(first, second));
        when(imageService.getDiaryImageSignedUrls(List.of(
                "oci://diary/object-a.png",
                "oci://diary/object-b.png",
                "oci://diary/object-c.png")))
                .thenReturn(reactor.core.publisher.Mono.just(List.of(
                        "https://signed.example/a.png",
                        "https://signed.example/b.png",
                        "https://signed.example/c.png")));

        List<DiaryResponseDto> response = begaDiaryService.getAllDiaries(10L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getPhotos()).containsExactly(
                "https://signed.example/a.png",
                "https://signed.example/b.png");
        assertThat(response.get(1).getPhotos()).containsExactly("https://signed.example/c.png");
        verify(imageService).getDiaryImageSignedUrls(List.of(
                "oci://diary/object-a.png",
                "oci://diary/object-b.png",
                "oci://diary/object-c.png"));
    }

    private BegaDiary createDiary(Long diaryId, Long ownerId, List<String> photoUrls) {
        UserEntity owner = UserEntity.builder()
                .id(ownerId)
                .email("owner@test.com")
                .name("Owner")
                .build();

        BegaDiary diary = BegaDiary.builder()
                .diaryDate(LocalDate.of(2026, 3, 9))
                .memo("직관 기록")
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(BegaDiary.DiaryWinning.WIN)
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
}
