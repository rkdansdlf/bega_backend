package com.example.mate.service;

import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.notification.service.NotificationService;
import com.example.profile.storage.service.ProfileImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private UserProviderRepository userProviderRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProfileImageService profileImageService;

    @InjectMocks
    private PartyService partyService;

    @Test
    @DisplayName("getAllParties normalizes null searchQuery to empty string")
    void getAllParties_normalizesNullSearchQuery() {
        when(partyRepository.findPartiesWithFilter(any(), any(), any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(Page.empty());

        partyService.getAllParties(null, null, null, null, PageRequest.of(0, 10));

        verify(partyRepository).findPartiesWithFilter(
                isNull(),
                isNull(),
                isNull(),
                eq(""),
                anyList(),
                any(Pageable.class));
    }

    @Test
    @DisplayName("getAllParties trims and keeps non-empty searchQuery")
    void getAllParties_trimsSearchQuery() {
        when(partyRepository.findPartiesWithFilter(any(), any(), any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(Page.empty());

        partyService.getAllParties(null, null, null, "  kt  ", PageRequest.of(0, 10));

        verify(partyRepository).findPartiesWithFilter(
                isNull(),
                isNull(),
                isNull(),
                eq("kt"),
                anyList(),
                any(Pageable.class));
    }

    @Test
    @DisplayName("legacy supabase host image falls back to user profile path before URL resolution")
    void getPartyById_usesUserProfileFallbackForLegacyHostImage() {
        Party party = createParty(101L, 77L,
                "https://zyofzvnkputevakepbdm.supabase.co/storage/v1/object/sign/profile-images/profiles/77/old.png");
        when(partyRepository.findById(101L)).thenReturn(Optional.of(party));
        when(userRepository.findProfileImageUrlById(77L)).thenReturn(Optional.of("profiles/77/latest.png"));
        when(profileImageService.getProfileImageUrl("profiles/77/latest.png"))
                .thenReturn("https://oci.example/profiles/77/latest.png");

        PartyDTO.Response response = partyService.getPartyById(101L);

        assertThat(response.getHostProfileImageUrl()).isEqualTo("https://oci.example/profiles/77/latest.png");
        verify(profileImageService).getProfileImageUrl("profiles/77/latest.png");
    }

    @Test
    @DisplayName("valid host profile path is resolved without user profile lookup")
    void getPartyById_usesPartyHostProfileImageWhenAlreadyValid() {
        Party party = createParty(102L, 55L, "profiles/55/current.png");
        when(partyRepository.findById(102L)).thenReturn(Optional.of(party));
        when(profileImageService.getProfileImageUrl("profiles/55/current.png"))
                .thenReturn("https://oci.example/profiles/55/current.png");

        PartyDTO.Response response = partyService.getPartyById(102L);

        assertThat(response.getHostProfileImageUrl()).isEqualTo("https://oci.example/profiles/55/current.png");
        verify(userRepository, never()).findProfileImageUrlById(55L);
    }

    @Test
    @DisplayName("legacy local asset host image falls back to null when no usable user profile")
    void getAllParties_legacyLocalAssetFallsBackToNull() {
        Party party = createParty(103L, 88L, "/assets/default-avatar.png");
        when(partyRepository.findPartiesWithFilter(any(), any(), any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(party)));
        when(userRepository.findProfileImageUrlById(88L)).thenReturn(Optional.empty());
        when(profileImageService.getProfileImageUrl(null)).thenReturn(null);

        Page<PartyDTO.Response> result = partyService.getAllParties(null, null, null, "", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getHostProfileImageUrl()).isNull();
    }

    private Party createParty(Long id, Long hostId, String hostProfileImageUrl) {
        return Party.builder()
                .id(id)
                .hostId(hostId)
                .hostName("host-" + hostId)
                .hostProfileImageUrl(hostProfileImageUrl)
                .hostFavoriteTeam("KT")
                .hostBadge(Party.BadgeType.NEW)
                .hostRating(5.0)
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
}

