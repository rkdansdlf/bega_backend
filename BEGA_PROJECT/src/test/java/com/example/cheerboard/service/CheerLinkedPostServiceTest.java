package com.example.cheerboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.BegaDiary.DiaryType;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPost.ShareMode;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.LinkedContentRes;
import com.example.cheerboard.dto.LinkedContentUnavailableReason;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import com.example.mate.entity.Party;
import com.example.mate.entity.Party.PartyStatus;
import com.example.mate.repository.PartyRepository;

@ExtendWith(MockitoExtension.class)
class CheerLinkedPostServiceTest {

    @Mock
    private BegaDiaryRepository diaryRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private CheerPostRepo postRepository;

    private CheerLinkedPostService service;
    private UserEntity owner;
    private UserEntity stranger;

    @BeforeEach
    void setUp() {
        service = new CheerLinkedPostService(diaryRepository, partyRepository, postRepository);
        owner = UserEntity.builder().id(7L).build();
        stranger = UserEntity.builder().id(8L).build();
    }

    @Test
    void validateCreate_acceptsOwnedAttendedVerifiedDiary() {
        BegaDiary diary = diary(11L, owner, DiaryType.ATTENDED, true, game("LG", "KT"), "LG", "잠실");
        when(diaryRepository.findByIdWithOwnerGameAndPhotos(11L)).thenReturn(Optional.of(diary));

        assertThat(service.validateCreate(PostType.CHECKIN, checkinRequest(11L), owner).diaryId())
                .isEqualTo(11L);
    }

    @Test
    void validateCreate_hidesExistingStrangerDiaryBehindNotFound() {
        BegaDiary diary = diary(11L, owner, DiaryType.ATTENDED, true, game("LG", "KT"), "LG", "잠실");
        when(diaryRepository.findByIdWithOwnerGameAndPhotos(11L)).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.validateCreate(PostType.CHECKIN, checkinRequest(11L), stranger))
                .isInstanceOf(NotFoundBusinessException.class)
                .hasMessageContaining("다이어리")
                .satisfies(error -> assertBusinessError(error, HttpStatus.NOT_FOUND, "DIARY_NOT_FOUND"));
    }

    @Test
    void validateCreate_rejectsDiaryThatIsNotAttendedAndVerified() {
        BegaDiary diary = diary(12L, owner, DiaryType.SCHEDULED, true, game("LG", "KT"), "LG", "잠실");
        when(diaryRepository.findByIdWithOwnerGameAndPhotos(12L)).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.validateCreate(PostType.CHECKIN, checkinRequest(12L), owner))
                .isInstanceOf(ConflictBusinessException.class)
                .satisfies(error -> assertBusinessError(error, HttpStatus.CONFLICT, "CHECKIN_NOT_SHAREABLE"));
    }

    @Test
    void validateCreate_requiresPartyHost() {
        Party party = party(21L, owner.getId(), PartyStatus.PENDING);
        when(partyRepository.findById(21L)).thenReturn(Optional.of(party));

        assertThatThrownBy(() -> service.validateCreate(PostType.RECRUITMENT, recruitmentRequest(21L), stranger))
                .satisfies(error -> assertBusinessError(error, HttpStatus.FORBIDDEN, "PARTY_HOST_REQUIRED"));
    }

    @Test
    void validateCreate_requiresPendingParty() {
        Party party = party(22L, owner.getId(), PartyStatus.MATCHED);
        when(partyRepository.findById(22L)).thenReturn(Optional.of(party));

        assertThatThrownBy(() -> service.validateCreate(PostType.RECRUITMENT, recruitmentRequest(22L), owner))
                .satisfies(error -> assertBusinessError(error, HttpStatus.CONFLICT, "PARTY_NOT_RECRUITING"));
    }

    @Test
    void validateCreate_namesEveryMissingDiaryBaseballField() {
        GameEntity incompleteGame = game(null, null);
        incompleteGame.setGameDate(null);
        BegaDiary diary = diary(13L, owner, DiaryType.ATTENDED, true, incompleteGame, null, null);
        when(diaryRepository.findByIdWithOwnerGameAndPhotos(13L)).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.validateCreate(PostType.CHECKIN, checkinRequest(13L), owner))
                .isInstanceOf(ManualBaseballDataRequiredException.class)
                .satisfies(error -> {
                    BusinessException businessError = (BusinessException) error;
                    assertThat(businessError.getCode()).isEqualTo("MANUAL_BASEBALL_DATA_REQUIRED");
                    ManualBaseballDataRequest request = (ManualBaseballDataRequest) businessError.getData();
                    assertThat(request.scope()).isEqualTo("cheer-linked:CHECKIN:13");
                    assertThat(request.missingItems())
                            .extracting(item -> item.key())
                            .containsExactly("gameDate", "homeTeam", "awayTeam", "cheeringTeam", "stadium");
                });
    }

    @Test
    void validateCreate_enforcesTypeSourceAndInternalShareCombinations() {
        assertInvalid(() -> service.validateCreate(PostType.CHECKIN, request(11L, 21L, null, null), owner));
        assertInvalid(() -> service.validateCreate(PostType.RECRUITMENT, request(null, null, null, null), owner));
        assertInvalid(() -> service.validateCreate(PostType.NORMAL, request(11L, null, null, null), owner));
        assertInvalid(() -> service.validateCreate(PostType.NOTICE, request(null, 21L, null, null), owner));
        assertInvalid(() -> service.validateCreate(
                PostType.CHECKIN, request(11L, null, ShareMode.EXTERNAL_LINK, null), owner));
        assertInvalid(() -> service.validateCreate(
                PostType.RECRUITMENT, request(null, 21L, ShareMode.INTERNAL_QUOTE, null), owner));
        assertInvalid(() -> service.validateCreate(
                PostType.CHECKIN, request(11L, null, ShareMode.INTERNAL_REPOST, "present-even-when-blank"), owner));
    }

    @Test
    void lookup_requiresExactlyOneSourceId() {
        assertInvalid(() -> service.lookup(null, null, owner));
        assertInvalid(() -> service.lookup(11L, 21L, owner));
    }

    @Test
    void lookupReturnsSafeCheckinPreviewAndActivePostId() {
        BegaDiary diary = diary(11L, owner, DiaryType.ATTENDED, true, game("LG", "KT"), "LG", "잠실");
        CheerPost post = CheerPost.builder().id(101L).postType(PostType.CHECKIN).diaryId(11L).build();
        when(diaryRepository.findByIdWithOwnerGameAndPhotos(11L)).thenReturn(Optional.of(diary));
        when(postRepository.findFirstByDiaryIdAndDeletedFalse(11L)).thenReturn(Optional.of(post));

        var result = service.lookup(11L, null, owner);

        assertThat(result.postId()).isEqualTo(101L);
        assertThat(result.preview().checkin()).satisfies(preview -> {
            assertThat(preview.gameDate()).isEqualTo(LocalDate.of(2026, 7, 13));
            assertThat(preview.homeTeam()).isEqualTo("LG");
            assertThat(preview.awayTeam()).isEqualTo("KT");
            assertThat(preview.cheeringTeam()).isEqualTo("LG");
            assertThat(preview.stadium()).isEqualTo("잠실");
            assertThat(preview.verified()).isTrue();
        });
        assertThat(result.preview().checkin().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("diaryId", "memo", "photoUrls", "ticketVerifiedAt", "section", "seatRow");
    }

    @Test
    void resolveForPostsMapsMissingAndIneligibleSources() {
        CheerPost missingDiaryFk = linkedPost(101L, PostType.CHECKIN, null, null);
        CheerPost ineligibleDiary = linkedPost(102L, PostType.CHECKIN, 12L, null);
        CheerPost failedParty = linkedPost(103L, PostType.RECRUITMENT, null, 23L);
        BegaDiary diary = diary(12L, owner, DiaryType.SCHEDULED, true, game("LG", "KT"), "LG", "잠실");
        Party party = party(23L, owner.getId(), PartyStatus.FAILED);
        when(diaryRepository.findAllByIdInWithOwnerAndGame(Set.of(12L))).thenReturn(List.of(diary));
        when(partyRepository.findByIdIn(Set.of(23L))).thenReturn(List.of(party));

        Map<Long, LinkedContentRes> result = service.resolveForPosts(
                List.of(missingDiaryFk, ineligibleDiary, failedParty));

        assertUnavailable(result.get(101L), LinkedContentUnavailableReason.SOURCE_MISSING);
        assertUnavailable(result.get(102L), LinkedContentUnavailableReason.SOURCE_INELIGIBLE);
        assertUnavailable(result.get(103L), LinkedContentUnavailableReason.SOURCE_INELIGIBLE);
    }

    @Test
    void resolveForPostsUsesOneQueryPerSourceTypeAndIncludesEmbeddedOriginals() {
        CheerPost checkin = linkedPost(101L, PostType.CHECKIN, 11L, null);
        CheerPost recruitment = linkedPost(102L, PostType.RECRUITMENT, null, 21L);
        CheerPost embeddedCheckin = linkedPost(103L, PostType.CHECKIN, 12L, null);
        CheerPost quote = CheerPost.builder().id(104L).postType(PostType.NORMAL).repostOf(embeddedCheckin).build();
        BegaDiary diary11 = diary(11L, owner, DiaryType.ATTENDED, true, game("LG", "KT"), "LG", "잠실");
        BegaDiary diary12 = diary(12L, owner, DiaryType.ATTENDED, true, game("SSG", "두산"), "SSG", "문학");
        Party party = party(21L, owner.getId(), PartyStatus.PENDING);
        when(diaryRepository.findAllByIdInWithOwnerAndGame(Set.of(11L, 12L)))
                .thenReturn(List.of(diary11, diary12));
        when(partyRepository.findByIdIn(Set.of(21L))).thenReturn(List.of(party));

        Map<Long, LinkedContentRes> result = service.resolveForPosts(List.of(checkin, recruitment, quote));

        assertThat(result).containsOnlyKeys(101L, 102L, 103L);
        assertThat(result.get(102L).recruitment().recruiting()).isTrue();
        verify(diaryRepository).findAllByIdInWithOwnerAndGame(Set.of(11L, 12L));
        verify(partyRepository).findByIdIn(Set.of(21L));
        verify(diaryRepository, never()).findById(11L);
        verify(diaryRepository, never()).findById(12L);
        verify(partyRepository, never()).findById(21L);
    }

    @Test
    void resolveForPostsKeepsClosedNonFailedPartyAvailable() {
        for (PartyStatus status : List.of(
                PartyStatus.MATCHED,
                PartyStatus.SELLING,
                PartyStatus.SOLD,
                PartyStatus.CHECKED_IN,
                PartyStatus.COMPLETED)) {
            Party party = party(21L, owner.getId(), status);
            when(partyRepository.findByIdIn(Set.of(21L))).thenReturn(List.of(party));

            LinkedContentRes resolved = service.resolveOne(linkedPost(101L, PostType.RECRUITMENT, null, 21L));

            assertThat(resolved.available()).isTrue();
            assertThat(resolved.recruitment().status()).isEqualTo(status.name());
            assertThat(resolved.recruitment().recruiting()).isFalse();
        }
    }

    @Test
    void resolveForPostsMapsMissingBaseballDataWithoutThrowing() {
        Party incompleteParty = party(21L, owner.getId(), PartyStatus.PENDING);
        incompleteParty.setHomeTeam(null);
        when(partyRepository.findByIdIn(Set.of(21L))).thenReturn(List.of(incompleteParty));

        LinkedContentRes result = service.resolveOne(linkedPost(101L, PostType.RECRUITMENT, null, 21L));

        assertUnavailable(result, LinkedContentUnavailableReason.MANUAL_BASEBALL_DATA_REQUIRED);
    }

    @Test
    void resolveForPostsDoesNotQueryRepositoriesForUnlinkedPosts() {
        CheerPost normal = CheerPost.builder().id(100L).postType(PostType.NORMAL).build();

        assertThat(service.resolveForPosts(List.of(normal))).isEmpty();
        verify(diaryRepository, never()).findAllByIdInWithOwnerAndGame(Set.of());
        verify(partyRepository, never()).findByIdIn(Set.of());
    }

    private static CreatePostReq checkinRequest(Long diaryId) {
        return request(diaryId, null, ShareMode.INTERNAL_REPOST, null);
    }

    private static CreatePostReq recruitmentRequest(Long partyId) {
        return request(null, partyId, ShareMode.INTERNAL_REPOST, null);
    }

    private static CreatePostReq request(Long diaryId, Long partyId, ShareMode shareMode, String sourceTitle) {
        return new CreatePostReq(
                "LG",
                "본문",
                List.of(),
                null,
                shareMode,
                null,
                sourceTitle,
                null,
                null,
                null,
                null,
                null,
                diaryId,
                partyId);
    }

    private static BegaDiary diary(
            Long id,
            UserEntity user,
            DiaryType type,
            boolean verified,
            GameEntity game,
            String cheeringTeam,
            String stadium) {
        BegaDiary diary = mock(BegaDiary.class);
        lenient().when(diary.getId()).thenReturn(id);
        lenient().when(diary.getUser()).thenReturn(user);
        lenient().when(diary.getType()).thenReturn(type);
        lenient().when(diary.isTicketVerified()).thenReturn(verified);
        lenient().when(diary.getGame()).thenReturn(game);
        lenient().when(diary.getTeam()).thenReturn(cheeringTeam);
        lenient().when(diary.getStadium()).thenReturn(stadium);
        return diary;
    }

    private static GameEntity game(String homeTeam, String awayTeam) {
        return GameEntity.builder()
                .id(31L)
                .gameId("20260713LGKT")
                .gameDate(LocalDate.of(2026, 7, 13))
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium("잠실")
                .build();
    }

    private static Party party(Long id, Long hostId, PartyStatus status) {
        return Party.builder()
                .id(id)
                .hostId(hostId)
                .gameDate(LocalDate.of(2026, 7, 13))
                .gameTime(LocalTime.of(18, 30))
                .homeTeam("LG")
                .awayTeam("KT")
                .stadium("잠실")
                .section("오렌지석")
                .currentParticipants(2)
                .maxParticipants(4)
                .status(status)
                .description("같이 응원해요")
                .price(10000)
                .ticketPrice(20000)
                .reservationDepositAmount(5000)
                .build();
    }

    private static CheerPost linkedPost(Long id, PostType type, Long diaryId, Long partyId) {
        return CheerPost.builder()
                .id(id)
                .postType(type)
                .diaryId(diaryId)
                .partyId(partyId)
                .build();
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BadRequestBusinessException.class)
                .satisfies(error -> assertBusinessError(
                        error, HttpStatus.BAD_REQUEST, "INVALID_LINKED_POST_REQUEST"));
    }

    private static void assertBusinessError(Object error, HttpStatus status, String code) {
        assertThat(error).isInstanceOf(BusinessException.class);
        BusinessException businessError = (BusinessException) error;
        assertThat(businessError.getStatus()).isEqualTo(status);
        assertThat(businessError.getCode()).isEqualTo(code);
    }

    private static void assertUnavailable(
            LinkedContentRes content,
            LinkedContentUnavailableReason reason) {
        assertThat(content.available()).isFalse();
        assertThat(content.unavailableReason()).isEqualTo(reason);
        assertThat(content.checkin()).isNull();
        assertThat(content.recruitment()).isNull();
    }
}
