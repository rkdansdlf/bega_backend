package com.example.mate.service;

import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.common.exception.BadRequestBusinessException;
import com.example.homepage.FeaturedMateCardDto;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyReviewRepository;
import com.example.notification.service.NotificationService;
import com.example.profile.storage.service.ProfileImageService;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartyService tests")
class PartyServiceTest {

        @Mock
        private PartyRepository partyRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private PartyApplicationRepository applicationRepository;

        @Mock
        private PartyReviewRepository partyReviewRepository;

        @Mock
        private UserProviderRepository userProviderRepository;

        @Mock
        private PartyFavoriteService partyFavoriteService;

        @Mock
        private PublicVisibilityVerifier publicVisibilityVerifier;

        @Mock
        private NotificationService notificationService;

        @Mock
        private ProfileImageService profileImageService;

        @Mock
        private TicketVerificationTokenStore ticketVerificationTokenStore;

        private PartyMapper partyMapper;
        private SimpleMeterRegistry mateHistoryMeterRegistry;

        private PartyService partyService;

        @BeforeEach
        void setUp() {
                mateHistoryMeterRegistry = new SimpleMeterRegistry();
                MateHistoryMetricsService mateHistoryMetricsService = new MateHistoryMetricsService(mateHistoryMeterRegistry);
                partyMapper = new PartyMapper(
                                userRepository,
                                partyRepository,
                                applicationRepository,
                                partyReviewRepository,
                                profileImageService);
                partyService = new PartyService(
                                partyRepository,
                                userRepository,
                                applicationRepository,
                                partyMapper,
                                partyFavoriteService,
                                userProviderRepository,
                                publicVisibilityVerifier,
                                ticketVerificationTokenStore,
                                notificationService,
                                mateHistoryMetricsService);
                lenient().when(publicVisibilityVerifier.canAccess(any(), any())).thenReturn(true);
                lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
                lenient().when(partyReviewRepository.findRatingSummariesByRevieweeIds(any())).thenReturn(List.of());
        }

        @Test
        @DisplayName("createParty derives host profile fields from authenticated user")
        void createParty_derivesHostFieldsFromAuthenticatedUser() {
                Long hostId = 77L;
                Principal principal = () -> "host@example.com";
                UserEntity host = UserEntity.builder()
                                .id(hostId)
                                .name("Real Host")
                                .email("host@example.com")
                                .role("ROLE_USER")
                                .profileImageUrl("profiles/77/current.png")
                                .build();

                when(userRepository.findByEmail("host@example.com")).thenReturn(Optional.of(host));
                when(userRepository.findById(hostId)).thenReturn(Optional.of(host));
                when(userProviderRepository.findByUserId(hostId))
                                .thenReturn(List.of(UserProvider.builder().provider("kakao").build()));
                when(applicationRepository.countCheckedInPartiesByUserId(hostId)).thenReturn(0);
                when(partyReviewRepository.findRatingSummariesByRevieweeIds(any()))
                                .thenReturn(List.of(ratingSummary(hostId, 4.7, 3L)));
                when(partyRepository.save(any(Party.class))).thenAnswer(inv -> {
                        Party party = inv.getArgument(0);
                        party.setId(500L);
                        return party;
                });
                when(ticketVerificationTokenStore.consumeToken("party-verification-token"))
                                .thenReturn(TicketInfo.builder()
                                                .date(LocalDate.now().plusDays(1).toString())
                                                .stadium("수원")
                                                .homeTeam("KT")
                                                .awayTeam("KIA")
                                                .build());

                PartyDTO.Request request = PartyDTO.Request.builder()
                                .gameDate(LocalDate.now().plusDays(1))
                                .gameTime(LocalTime.of(18, 30))
                                .stadium("수원")
                                .homeTeam("KT")
                                .awayTeam("KIA")
                                .cheeringSide(Party.CheeringSide.HOME)
                                .section("1루")
                                .seatDetail("305블록 12열 15번")
                                .maxParticipants(4)
                                .description("실제 사용자 프로필에서 생성해야 합니다.")
                                .ticketPrice(12000)
                                .verificationToken("party-verification-token")
                                .build();

                PartyDTO.Response response = partyService.createParty(request, principal);

                assertThat(response.getHostId()).isEqualTo(hostId);
                assertThat(response.getHostName()).isEqualTo("Real Host");
                assertThat(response.getHostBadge()).isEqualTo(Party.BadgeType.VERIFIED);
                assertThat(response.getHostAverageRating()).isEqualTo(4.7);
                assertThat(response.getHostReviewCount()).isEqualTo(3L);
                assertThat(response.getSeatDetail()).isEqualTo("305블록 12열 15번");
                verify(partyRepository).save(any(Party.class));
        }

        @Test
        @DisplayName("getAllParties normalizes null searchQuery to empty string")
        void getAllParties_normalizesNullSearchQuery() {
                // Force IDE re-sync for arguments match
                when(partyRepository.findVisiblePublicPartiesWithFilter(any(), any(), any(), any(), anyList(), any(),
                                any(), any(Pageable.class)))
                                .thenReturn(Page.empty());

                partyService.getAllParties(null, null, null, null, PageRequest.of(0, 10), null, null);

                verify(partyRepository).findVisiblePublicPartiesWithFilter(
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(""),
                                anyList(),
                                isNull(),
                                isNull(),
                                any(Pageable.class));
        }

        @Test
        @DisplayName("getAllParties trims and keeps non-empty searchQuery")
        void getAllParties_trimsSearchQuery() {
                when(partyRepository.findVisiblePublicPartiesWithFilter(any(), any(), any(), any(), anyList(), any(),
                                any(),
                                any(Pageable.class)))
                                .thenReturn(Page.empty());

                partyService.getAllParties(null, null, null, "  kt  ", PageRequest.of(0, 10), null, null);

                verify(partyRepository).findVisiblePublicPartiesWithFilter(
                                isNull(),
                                isNull(),
                                isNull(),
                                eq("kt"),
                                anyList(),
                                isNull(),
                                isNull(),
                                any(Pageable.class));
        }

        @Test
        @DisplayName("getAllParties applies status filter when status is provided")
        void getAllParties_filtersByStatus() {
                when(partyRepository.findVisiblePublicPartiesWithFilter(any(), any(), any(), any(), anyList(), any(),
                                any(),
                                any(Pageable.class)))
                                .thenReturn(Page.empty());

                partyService.getAllParties(null, null, null, null, PageRequest.of(0, 10), Party.PartyStatus.MATCHED, null);

                verify(partyRepository).findVisiblePublicPartiesWithFilter(
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(""),
                                anyList(),
                                eq(Party.PartyStatus.MATCHED),
                                isNull(),
                                any(Pageable.class));
        }

        @Test
        @DisplayName("legacy supabase host image resolves directly via profile image service")
        void getPartyById_usesSupabaseHostImageWithoutUserProfileFallback() {
                Party party = createParty(101L, 77L,
                                "https://zyofzvnkputevakepbdm.supabase.co/storage/v1/object/sign/profile-images/profiles/77/old.png");
                when(partyRepository.findById(101L)).thenReturn(Optional.of(party));
                when(profileImageService.getProfileImageUrlForUser(77L, "https://zyofzvnkputevakepbdm.supabase.co/storage/v1/object/sign/profile-images/profiles/77/old.png"))
                                .thenReturn("https://oci.example/profiles/77/latest.png");

                PartyDTO.PublicResponse response = partyService.getPartyById(101L, null);

                assertThat(response.getHostProfileImageUrl()).isEqualTo("https://oci.example/profiles/77/latest.png");
                verify(profileImageService).getProfileImageUrlForUser(77L,
                                "https://zyofzvnkputevakepbdm.supabase.co/storage/v1/object/sign/profile-images/profiles/77/old.png");
                verify(userRepository, never()).findProfileImageUrlById(77L);
        }

        @Test
        @DisplayName("valid host profile path is resolved without user profile lookup")
        void getPartyById_usesPartyHostProfileImageWhenAlreadyValid() {
                Party party = createParty(102L, 55L, "profiles/55/current.png");
                when(partyRepository.findById(102L)).thenReturn(Optional.of(party));
                when(profileImageService.getProfileImageUrlForUser(55L, "profiles/55/current.png"))
                                .thenReturn("https://oci.example/profiles/55/current.png");

                PartyDTO.PublicResponse response = partyService.getPartyById(102L, null);

                assertThat(response.getHostProfileImageUrl()).isEqualTo("https://oci.example/profiles/55/current.png");
                verify(userRepository, never()).findProfileImageUrlById(55L);
        }

        @Test
        @DisplayName("getPartyById public response includes host handle")
        void getPartyById_publicResponseIncludesHostHandle() {
                Party party = createParty(105L, 92L, null);
                when(partyRepository.findById(105L)).thenReturn(Optional.of(party));
                when(userRepository.findAllById(any())).thenReturn(List.of(
                                UserEntity.builder().id(92L).handle("testuser").name("Host 92").email("host92@example.com").build()));
                when(profileImageService.getProfileImageUrlForUser(92L, null)).thenReturn(null);

                PartyDTO.PublicResponse response = partyService.getPartyById(105L, null);

                assertThat(response.getHostHandle()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("getPartyById sets favorited when the current user favorited the party")
        void getPartyById_setsFavoritedForCurrentUser() {
                Party party = createParty(110L, 92L, null);
                when(partyRepository.findById(110L)).thenReturn(Optional.of(party));
                when(profileImageService.getProfileImageUrlForUser(92L, null)).thenReturn(null);
                when(partyFavoriteService.isFavorited(99L, 110L)).thenReturn(true);

                PartyDTO.PublicResponse response = partyService.getPartyById(110L, 99L);

                assertThat(response.getFavorited()).isTrue();
        }

        @Test
        @DisplayName("getPartyById leaves favorited false for an anonymous viewer (no favorite lookup)")
        void getPartyById_favoritedFalseForAnonymous() {
                Party party = createParty(111L, 92L, null);
                when(partyRepository.findById(111L)).thenReturn(Optional.of(party));
                when(profileImageService.getProfileImageUrlForUser(92L, null)).thenReturn(null);

                PartyDTO.PublicResponse response = partyService.getPartyById(111L, null);

                assertThat(response.getFavorited()).isFalse();
                verify(partyFavoriteService, never()).isFavorited(any(), any());
        }

        @Test
        @DisplayName("getPartyById builds member roster (host + approved) exposing only initial/avatar/role")
        void getPartyById_buildsMemberRoster() {
                Party party = createParty(112L, 92L, null);
                when(partyRepository.findById(112L)).thenReturn(Optional.of(party));
                when(profileImageService.getProfileImageUrlForUser(92L, null)).thenReturn(null);
                when(applicationRepository.findByPartyIdAndIsApprovedTrue(112L)).thenReturn(List.of(
                                PartyApplication.builder().partyId(112L).applicantId(200L).applicantName("민수").isApproved(true).build()));
                when(userRepository.findAllById(List.of(200L))).thenReturn(List.of(
                                UserEntity.builder().id(200L).profileImageUrl("profiles/200/a.png").build()));

                PartyDTO.PublicResponse response = partyService.getPartyById(112L, null);

                assertThat(response.getMembers()).hasSize(2);
                assertThat(response.getMembers().get(0).isHost()).isTrue();
                assertThat(response.getMembers().get(0).getRole()).isEqualTo("호스트");
                assertThat(response.getMembers().get(0).getInitial()).isEqualTo("h"); // host-92 → 'h'
                assertThat(response.getMembers().get(1).isHost()).isFalse();
                assertThat(response.getMembers().get(1).getRole()).isEqualTo("메이트");
                assertThat(response.getMembers().get(1).getInitial()).isEqualTo("민");
                assertThat(response.getMembers().get(1).getProfileImageUrl()).isEqualTo("profiles/200/a.png");
        }

        @Test
        @DisplayName("legacy local asset host image falls back to null when no usable user profile")
        void getAllParties_legacyLocalAssetFallsBackToNull() {
                Party party = createParty(103L, 88L, "/assets/default-avatar.png");
                when(partyRepository.findVisiblePublicPartiesWithFilter(any(), any(), any(), any(), anyList(), any(),
                                any(),
                                any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(party)));

                Page<PartyDTO.PublicResponse> result = partyService.getAllParties(null, null, null, "", PageRequest.of(0, 10),
                                null, null);

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getHostProfileImageUrl()).isNull();
                assertThat(result.getContent().get(0).getHostAverageRating()).isNull();
                assertThat(result.getContent().get(0).getHostReviewCount()).isZero();
        }

        @Test
        @DisplayName("getAllParties includes review summary for consistent host rating display")
        void getAllParties_includesReviewSummary() {
                Party party = createParty(104L, 91L, null);
                when(partyRepository.findVisiblePublicPartiesWithFilter(any(), any(), any(), any(), anyList(), any(),
                                any(),
                                any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(party)));
                when(profileImageService.getProfileImageUrlForUser(91L, null)).thenReturn(null);
                when(partyReviewRepository.findRatingSummariesByRevieweeIds(any()))
                                .thenReturn(List.of(ratingSummary(91L, 4.4, 2L)));

                Page<PartyDTO.PublicResponse> result = partyService.getAllParties(
                                null,
                                null,
                                null,
                                "",
                                PageRequest.of(0, 10),
                                null,
                                null);

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getHostAverageRating()).isEqualTo(4.4);
                assertThat(result.getContent().get(0).getHostReviewCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("getAllParties sets favorite flags for authenticated viewers in one lookup")
        void getAllParties_setsFavoriteFlagsForAuthenticatedViewer() {
                Party first = createParty(301L, 11L, null);
                Party second = createParty(302L, 12L, null);
                when(partyRepository.findVisiblePublicPartiesWithFilter(any(), any(), any(), any(), anyList(), any(),
                                any(),
                                any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(first, second)));
                when(partyFavoriteService.getFavoritePartyIds(99L, List.of(301L, 302L))).thenReturn(List.of(302L));

                Page<PartyDTO.PublicResponse> result = partyService.getAllParties(
                                null,
                                null,
                                null,
                                "",
                                PageRequest.of(0, 10),
                                null,
                                99L);

                assertThat(result.getContent()).extracting(PartyDTO.PublicResponse::getFavorited)
                                .containsExactly(false, true);
                verify(partyFavoriteService).getFavoritePartyIds(99L, List.of(301L, 302L));
                verify(partyFavoriteService, never()).getFavoritePartyIds(99L);
        }

        @Test
        @DisplayName("getAllParties batches host lookups for multiple visible parties")
        void getAllParties_batchesHostLookupsForMultipleParties() {
                Party first = createParty(301L, 11L, null);
                Party second = createParty(302L, 12L, null);
                Party third = createParty(303L, 11L, null);

                when(partyRepository.findVisiblePublicPartiesWithFilter(any(), any(), any(), any(), anyList(), any(),
                                any(),
                                any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(first, second, third)));
                when(userRepository.findAllById(any())).thenReturn(List.of(
                                UserEntity.builder().id(11L).handle("@host11").name("Host 11").email("host11@example.com").build(),
                                UserEntity.builder().id(12L).handle("@host12").name("Host 12").email("host12@example.com").build()));
                when(profileImageService.getProfileImageUrlForUser(any(), any())).thenReturn(null);

                Page<PartyDTO.PublicResponse> result = partyService.getAllParties(
                                null,
                                null,
                                null,
                                "",
                                PageRequest.of(0, 10),
                                null,
                                null);

                assertThat(result.getContent()).hasSize(3);
                verify(userRepository, times(1)).findAllById(argThat((Iterable<Long> hostIds) -> {
                        List<Long> ids = new ArrayList<>();
                        hostIds.forEach(ids::add);
                        return ids.size() == 2 && ids.containsAll(List.of(11L, 12L));
                }));
                verify(userRepository, never()).findById(11L);
                verify(userRepository, never()).findById(12L);
        }

        @Test
        @DisplayName("getFeaturedMateCards returns lightweight upcoming pending cards only")
        void getFeaturedMateCards_returnsLightweightCardsOnly() {
                LocalDate today = LocalDate.now();
                List<Party> parties = List.of(
                                createUpcomingPendingParty(201L, 1L, today.plusDays(1), LocalTime.of(18, 30)),
                                createUpcomingPendingParty(202L, 2L, today.plusDays(2), LocalTime.of(18, 30)),
                                createUpcomingPendingParty(203L, 3L, today.plusDays(3), LocalTime.of(18, 30)),
                                createUpcomingPendingParty(204L, 4L, today.plusDays(4), LocalTime.of(18, 30)),
                                createUpcomingPendingParty(205L, 5L, today.plusDays(5), LocalTime.of(18, 30)));

                when(partyRepository.findByStatusAndGameDateGreaterThanEqual(
                                eq(Party.PartyStatus.PENDING),
                                eq(today),
                                any(Pageable.class)))
                                .thenReturn(new PageImpl<>(parties));
                when(userRepository.findAllById(any())).thenReturn(List.of(
                                UserEntity.builder().id(1L).build(),
                                UserEntity.builder().id(2L).build(),
                                UserEntity.builder().id(3L).build(),
                                UserEntity.builder().id(4L).build(),
                                UserEntity.builder().id(5L).build()));

                List<FeaturedMateCardDto> result = partyService.getFeaturedMateCards(today, 4);

                assertThat(result).hasSize(4);
                assertThat(result).extracting(FeaturedMateCardDto::getId)
                                .containsExactly(201L, 202L, 203L, 204L);
                assertThat(result.get(0).getTeamId()).isEqualTo("KT");
                assertThat(result.get(0).getStadium()).isEqualTo("수원");
                assertThat(result.get(0).getSection()).isEqualTo("응원석");
                verify(profileImageService, never()).getProfileImageUrlForUser(any(), any());
                verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("getMyPartyHistory returns lightweight paged history responses")
        void getMyPartyHistory_returnsPagedHistoryResponses() {
                Party party = createParty(801L, 42L, null);
                when(partyRepository.findMyHistory(eq(42L), isNull(), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(party), PageRequest.of(0, 20), 1));

                Page<PartyDTO.HistoryResponse> result = partyService.getMyPartyHistory(
                                42L,
                                "all",
                                PageRequest.of(0, 20));

                assertThat(result.getTotalElements()).isEqualTo(1);
                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getId()).isEqualTo(801L);
                assertThat(result.getContent().get(0).getStadium()).isEqualTo("수원");
                assertThat(mateHistoryTimer("all", "success").count()).isEqualTo(1L);
                verify(partyRepository).findMyHistory(eq(42L), isNull(), any(Pageable.class));
                verify(profileImageService, never()).getProfileImageUrlForUser(any(), any());
        }

        @Test
        @DisplayName("getMyPartyHistory maps completed group to completed statuses")
        void getMyPartyHistory_completedGroupMapsStatuses() {
                when(partyRepository.findMyHistory(eq(42L), anyList(), any(Pageable.class)))
                                .thenReturn(Page.empty());

                partyService.getMyPartyHistory(42L, "completed", PageRequest.of(0, 20));

                verify(partyRepository).findMyHistory(
                                eq(42L),
                                argThat(statuses -> statuses.equals(List.of(
                                                Party.PartyStatus.COMPLETED,
                                                Party.PartyStatus.CHECKED_IN))),
                                any(Pageable.class));
        }

        @Test
        @DisplayName("getMyPartyHistory maps ongoing group to active statuses")
        void getMyPartyHistory_ongoingGroupMapsStatuses() {
                when(partyRepository.findMyHistory(eq(42L), anyList(), any(Pageable.class)))
                                .thenReturn(Page.empty());

                partyService.getMyPartyHistory(42L, "ongoing", PageRequest.of(0, 20));

                verify(partyRepository).findMyHistory(
                                eq(42L),
                                argThat(statuses -> statuses.equals(List.of(
                                                Party.PartyStatus.PENDING,
                                                Party.PartyStatus.MATCHED))),
                                any(Pageable.class));
        }

        @Test
        @DisplayName("getMyPartyHistory rejects unsupported group")
        void getMyPartyHistory_invalidGroupThrows() {
                assertThrows(
                                BadRequestBusinessException.class,
                                () -> partyService.getMyPartyHistory(42L, "archived", PageRequest.of(0, 20)));

                assertThat(mateHistoryTimer("invalid", "failure").count()).isEqualTo(1L);
                verify(partyRepository, never()).findMyHistory(any(), any(), any());
        }

        private Timer mateHistoryTimer(String group, String result) {
                return mateHistoryMeterRegistry.get(MateHistoryMetricsService.REQUEST_DURATION_METRIC)
                                .tag("group", group)
                                .tag("result", result)
                                .timer();
        }

        // ========== incrementParticipants() ==========

        @Test
        @DisplayName("incrementParticipants - 정원 도달 시 MATCHED로 자동 전환")
        void incrementParticipants_autoTransitionsToMatchedWhenFull() {
                Party party = createParty(1L, 10L, null);
                party.setCurrentParticipants(3); // maxParticipants=4, one away from full

                when(partyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(party));
                when(partyRepository.save(any(Party.class))).thenAnswer(inv -> inv.getArgument(0));

                PartyDTO.Response response = partyService.incrementParticipants(1L);

                assertThat(response.getStatus()).isEqualTo(Party.PartyStatus.MATCHED);
                assertThat(response.getCurrentParticipants()).isEqualTo(4);
        }

        @Test
        @DisplayName("incrementParticipants - 정원 초과 시 PartyFullException 발생")
        void incrementParticipants_throwsPartyFullExceptionWhenAtCapacity() {
                Party party = createParty(1L, 10L, null);
                party.setCurrentParticipants(4); // already at max

                when(partyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(party));

                assertThrows(PartyFullException.class, () -> partyService.incrementParticipants(1L));
                verify(partyRepository, never()).save(any());
        }

        @Test
        @DisplayName("getPartiesByHostHandle validates host visibility before returning public parties")
        void getPartiesByHostHandle_validatesVisibility() {
                UserEntity host = UserEntity.builder().id(77L).handle("@host").build();
                Party party = createParty(500L, 77L, null);

                when(userRepository.findByHandle("@host")).thenReturn(Optional.of(host));
                when(partyRepository.findByHostId(77L)).thenReturn(List.of(party));

                List<PartyDTO.PublicResponse> result = partyService.getPartiesByHostHandle("@host", 99L);

                assertThat(result).hasSize(1);
                verify(publicVisibilityVerifier).validate(host, 99L, "파티");
        }

        @Test
        @DisplayName("updateParty - 호스트는 파티를 수정할 수 있다")
        void updateParty_hostCanUpdateParty() {
                Party party = createParty(700L, 10L, null);
                PartyDTO.UpdateRequest request = PartyDTO.UpdateRequest.builder()
                                .description("수정된 소개글입니다.")
                                .seatDetail("306블록 10열 8번")
                                .maxParticipants(5)
                                .build();

                when(partyRepository.findByIdAndHostIdForUpdate(700L, 10L)).thenReturn(Optional.of(party));
                when(applicationRepository.countByPartyIdAndIsApprovedTrue(700L)).thenReturn(0L);
                when(partyRepository.save(any(Party.class))).thenAnswer(inv -> inv.getArgument(0));

                PartyDTO.Response response = partyService.updateParty(700L, request, 10L);

                assertThat(response.getDescription()).isEqualTo("수정된 소개글입니다.");
                assertThat(response.getSeatDetail()).isEqualTo("306블록 10열 8번");
                assertThat(response.getMaxParticipants()).isEqualTo(5);
                assertThat(party.getSearchText()).contains("306블록 10열 8번");
                verify(partyRepository).findByIdAndHostIdForUpdate(700L, 10L);
                verify(partyRepository, never()).findById(700L);
        }

        @Test
        @DisplayName("updateParty - 비호스트는 없는 파티처럼 처리한다")
        void updateParty_nonHostIsTreatedAsNotFound() {
                PartyDTO.UpdateRequest request = PartyDTO.UpdateRequest.builder()
                                .description("수정된 소개글입니다.")
                                .build();

                when(partyRepository.findByIdAndHostIdForUpdate(700L, 99L)).thenReturn(Optional.empty());

                assertThrows(PartyNotFoundException.class, () -> partyService.updateParty(700L, request, 99L));

                verify(partyRepository).findByIdAndHostIdForUpdate(700L, 99L);
                verify(partyRepository, never()).findById(700L);
                verify(partyRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateParty - 호스트가 수명주기 상태를 직접 변경할 수 없다")
        void updateParty_rejectsHostDrivenLifecycleStatusChange() {
                Party party = createParty(702L, 10L, null);
                PartyDTO.UpdateRequest request = PartyDTO.UpdateRequest.builder()
                                .status(Party.PartyStatus.COMPLETED)
                                .build();

                when(partyRepository.findByIdAndHostIdForUpdate(702L, 10L)).thenReturn(Optional.of(party));
                when(applicationRepository.countByPartyIdAndIsApprovedTrue(702L)).thenReturn(0L);

                assertThrows(InvalidApplicationStatusException.class,
                                () -> partyService.updateParty(702L, request, 10L));
                verify(partyRepository, never()).save(any());
        }

        @Test
        @DisplayName("deleteParty - 호스트는 파티를 삭제할 수 있다")
        void deleteParty_hostCanDeleteParty() {
                Party party = createParty(701L, 10L, null);

                when(partyRepository.findByIdAndHostIdForUpdate(701L, 10L)).thenReturn(Optional.of(party));
                when(applicationRepository.findByPartyIdAndIsApprovedTrue(701L)).thenReturn(List.of());
                when(applicationRepository.findByPartyId(701L)).thenReturn(List.of());

                partyService.deleteParty(701L, 10L);

                verify(partyRepository).findByIdAndHostIdForUpdate(701L, 10L);
                verify(partyRepository, never()).findById(701L);
                verify(partyRepository).delete(party);
        }

        @Test
        @DisplayName("deleteParty - 결제된 대기 신청이 있으면 삭제할 수 없다")
        void deleteParty_paidPendingApplicationBlocksDeletion() {
                Party party = createParty(703L, 10L, null);
                PartyApplication paidPending = PartyApplication.builder()
                                .id(900L)
                                .partyId(703L)
                                .isPaid(true)
                                .isApproved(false)
                                .isRejected(false)
                                .build();

                when(partyRepository.findByIdAndHostIdForUpdate(703L, 10L)).thenReturn(Optional.of(party));
                when(applicationRepository.findByPartyIdAndIsApprovedTrue(703L)).thenReturn(List.of());
                when(applicationRepository.findByPartyId(703L)).thenReturn(List.of(paidPending));

                InvalidApplicationStatusException exception = assertThrows(
                                InvalidApplicationStatusException.class,
                                () -> partyService.deleteParty(703L, 10L));

                assertThat(exception.getMessage()).contains("결제");
                verify(applicationRepository, never()).deleteAll(anyList());
                verify(partyRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deleteParty - 비호스트는 없는 파티처럼 처리한다")
        void deleteParty_nonHostIsTreatedAsNotFound() {
                when(partyRepository.findByIdAndHostIdForUpdate(701L, 99L)).thenReturn(Optional.empty());

                assertThrows(PartyNotFoundException.class, () -> partyService.deleteParty(701L, 99L));

                verify(partyRepository).findByIdAndHostIdForUpdate(701L, 99L);
                verify(partyRepository, never()).findById(701L);
                verify(partyRepository, never()).delete(any());
        }

        // ========== handleUserDeletion() ==========

        @Test
        @DisplayName("handleUserDeletion - 호스트 파티를 FAILED로 변경하고 save 호출")
        void handleUserDeletion_setsHostedPartiesToFailed() {
                Long userId = 99L;
                Party hostedParty = createParty(1L, userId, null);

                when(partyRepository.findByHostIdAndStatusIn(eq(userId), anyList()))
                                .thenReturn(List.of(hostedParty));
                when(partyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(hostedParty));
                when(applicationRepository.findByPartyIdAndIsApprovedTrue(1L))
                                .thenReturn(List.of());

                partyService.handleUserDeletion(userId);

                assertThat(hostedParty.getStatus()).isEqualTo(Party.PartyStatus.FAILED);
                verify(partyRepository).save(hostedParty);
        }

        @Test
        @DisplayName("handleUserDeletion - 참여 중인 MATCHED 파티를 PENDING으로 되돌리고 신청 삭제")
        void handleUserDeletion_revertsMatchedToPendingWhenParticipantLeaves() {
                Long userId = 99L;
                Party matchedParty = createParty(2L, 10L, null);
                matchedParty.setStatus(Party.PartyStatus.MATCHED);
                matchedParty.setCurrentParticipants(2);

                PartyApplication application = mock(PartyApplication.class);
                when(application.getId()).thenReturn(55L);
                when(application.getPartyId()).thenReturn(2L);
                when(application.getIsApproved()).thenReturn(true);
                when(application.getIsRejected()).thenReturn(false);
                when(application.getApplicantName()).thenReturn("DeletedUser");

                when(partyRepository.findByHostIdAndStatusIn(eq(userId), anyList()))
                                .thenReturn(List.of());
                when(applicationRepository.findByApplicantId(userId))
                                .thenReturn(List.of(application));
                when(partyRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(matchedParty));
                when(applicationRepository.findByIdAndApplicantIdForUpdate(55L, userId))
                                .thenReturn(Optional.of(application));

                partyService.handleUserDeletion(userId);

                assertThat(matchedParty.getStatus()).isEqualTo(Party.PartyStatus.PENDING);
                assertThat(matchedParty.getCurrentParticipants()).isEqualTo(1);
                verify(applicationRepository).delete(application);
        }

        private Party createParty(Long id, Long hostId, String hostProfileImageUrl) {
                return Party.builder()
                                .id(id)
                                .hostId(hostId)
                                .hostName("host-" + hostId)
                                .hostProfileImageUrl(hostProfileImageUrl)
                                .hostFavoriteTeam("KT")
                                .hostBadge(Party.BadgeType.NEW)
                                .teamId("KT")
                                .gameDate(LocalDate.now().plusDays(1))
                                .gameTime(LocalTime.of(18, 30))
                                .stadium("수원")
                                .homeTeam("KT")
                                .awayTeam("KIA")
                                .section("응원석")
                                .maxParticipants(4)
                                .currentParticipants(1)
                                .description("같이 응원")
                                .ticketVerified(false)
                                .status(Party.PartyStatus.PENDING)
                                .build();
        }

        private Party createUpcomingPendingParty(Long id, Long hostId, LocalDate gameDate, LocalTime gameTime) {
                Party party = createParty(id, hostId, null);
                party.setGameDate(gameDate);
                party.setGameTime(gameTime);
                party.setStatus(Party.PartyStatus.PENDING);
                return party;
        }

        private PartyReviewRepository.RevieweeRatingSummary ratingSummary(Long revieweeId, Double averageRating, Long reviewCount) {
                return new PartyReviewRepository.RevieweeRatingSummary() {
                        @Override
                        public Long getRevieweeId() {
                                return revieweeId;
                        }

                        @Override
                        public Double getAverageRating() {
                                return averageRating;
                        }

                        @Override
                        public Long getReviewCount() {
                                return reviewCount;
                        }
                };
        }
}
