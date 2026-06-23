package com.example.mate.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.UserService;
import com.example.mate.dto.PartyReviewDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PartyReview;
import com.example.mate.exception.InvalidReviewException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartyReviewService tests")
class PartyReviewServiceTest {

    @Mock
    private PartyReviewRepository partyReviewRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyApplicationRepository partyApplicationRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private PartyReviewService partyReviewService;

    @Test
    @DisplayName("createReview resolves reviewee by handle and returns reviewer/reviewee handles")
    void createReview_resolvesRevieweeByHandle() {
        Principal principal = () -> "reviewer@example.com";
        Party party = Party.builder()
                .id(7L)
                .hostId(100L)
                .gameDate(LocalDate.of(2026, 3, 9))
                .gameTime(LocalTime.of(18, 30))
                .status(Party.PartyStatus.COMPLETED)
                .build();

        when(userService.getUserIdByEmail("reviewer@example.com")).thenReturn(100L);
        when(partyRepository.findAccessibleByIdAndParticipantId(7L, 100L)).thenReturn(Optional.of(party));
        when(userService.getUserIdByHandle("@guest")).thenReturn(200L);
        when(userService.findUserById(100L)).thenReturn(UserEntity.builder().id(100L).handle("@host").build());
        when(userService.findUserById(200L)).thenReturn(UserEntity.builder().id(200L).handle("@guest").build());
        when(partyApplicationRepository.findByPartyIdAndApplicantId(7L, 200L))
                .thenReturn(Optional.of(PartyApplication.builder().partyId(7L).applicantId(200L).isApproved(true).build()));
        when(partyReviewRepository.existsByPartyIdAndReviewerIdAndRevieweeId(7L, 100L, 200L)).thenReturn(false);
        when(partyReviewRepository.save(any(PartyReview.class))).thenAnswer(invocation -> {
            PartyReview review = invocation.getArgument(0);
            review.setId(999L);
            return review;
        });

        PartyReviewDTO.Response response = partyReviewService.createReview(PartyReviewDTO.Request.builder()
                .partyId(7L)
                .revieweeHandle("@guest")
                .rating(5)
                .comment("좋았어요")
                .build(), principal);

        assertThat(response.getReviewerHandle()).isEqualTo("@host");
        assertThat(response.getRevieweeHandle()).isEqualTo("@guest");
    }

    @Test
    @DisplayName("createReview treats a non-participant reviewer as not found before resolving reviewee")
    void createReview_nonParticipantReviewerIsTreatedAsNotFound() {
        Principal principal = () -> "reviewer@example.com";

        when(userService.getUserIdByEmail("reviewer@example.com")).thenReturn(300L);
        when(partyRepository.findAccessibleByIdAndParticipantId(7L, 300L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partyReviewService.createReview(PartyReviewDTO.Request.builder()
                .partyId(7L)
                .revieweeHandle("@guest")
                .rating(5)
                .build(), principal))
                .isInstanceOf(PartyNotFoundException.class);

        verify(userService, never()).getUserIdByHandle("@guest");
        verify(partyReviewRepository, never()).save(any(PartyReview.class));
    }

    @Test
    @DisplayName("createReview keeps the existing domain error after reviewer access passes")
    void createReview_incompletePartyAfterAccessStillThrowsInvalidReview() {
        Principal principal = () -> "reviewer@example.com";
        Party party = Party.builder()
                .id(7L)
                .hostId(100L)
                .status(Party.PartyStatus.MATCHED)
                .build();

        when(userService.getUserIdByEmail("reviewer@example.com")).thenReturn(100L);
        when(partyRepository.findAccessibleByIdAndParticipantId(7L, 100L)).thenReturn(Optional.of(party));
        when(userService.getUserIdByHandle("@guest")).thenReturn(200L);

        assertThatThrownBy(() -> partyReviewService.createReview(PartyReviewDTO.Request.builder()
                .partyId(7L)
                .revieweeHandle("@guest")
                .rating(5)
                .build(), principal))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("완료된 파티");

        verify(partyReviewRepository, never()).save(any(PartyReview.class));
    }

    @Test
    @DisplayName("createReview rejects a non-participant reviewee after reviewer access passes")
    void createReview_nonParticipantRevieweeThrowsInvalidReview() {
        Principal principal = () -> "reviewer@example.com";
        Party party = Party.builder()
                .id(7L)
                .hostId(100L)
                .status(Party.PartyStatus.COMPLETED)
                .build();

        when(userService.getUserIdByEmail("reviewer@example.com")).thenReturn(100L);
        when(partyRepository.findAccessibleByIdAndParticipantId(7L, 100L)).thenReturn(Optional.of(party));
        when(userService.getUserIdByHandle("@outsider")).thenReturn(300L);
        when(partyApplicationRepository.findByPartyIdAndApplicantId(7L, 300L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partyReviewService.createReview(PartyReviewDTO.Request.builder()
                .partyId(7L)
                .revieweeHandle("@outsider")
                .rating(5)
                .build(), principal))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("파티 참여자");

        verify(partyReviewRepository, never()).save(any(PartyReview.class));
    }

    @Test
    @DisplayName("getReviewsByParty includes reviewer and reviewee handles")
    void getReviewsByParty_includesHandles() {
        Party party = Party.builder()
                .id(7L)
                .hostId(100L)
                .status(Party.PartyStatus.COMPLETED)
                .build();

        when(partyRepository.findAccessibleByIdAndParticipantId(7L, 100L)).thenReturn(Optional.of(party));
        when(userService.findUserById(100L)).thenReturn(UserEntity.builder().id(100L).handle("@host").build());
        when(userService.findUserById(200L)).thenReturn(UserEntity.builder().id(200L).handle("@guest").build());
        when(partyReviewRepository.findByPartyId(7L)).thenReturn(List.of(
                PartyReview.builder()
                        .id(1L)
                        .partyId(7L)
                        .reviewerId(100L)
                        .revieweeId(200L)
                        .rating(4)
                        .build()));

        List<PartyReviewDTO.Response> responses = partyReviewService.getReviewsByParty(7L, 100L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getReviewerHandle()).isEqualTo("@host");
        assertThat(responses.get(0).getRevieweeHandle()).isEqualTo("@guest");
    }

    @Test
    @DisplayName("getReviewsByParty allows an approved participant")
    void getReviewsByParty_allowsApprovedParticipant() {
        Party party = Party.builder()
                .id(7L)
                .hostId(100L)
                .status(Party.PartyStatus.COMPLETED)
                .build();

        when(partyRepository.findAccessibleByIdAndParticipantId(7L, 200L)).thenReturn(Optional.of(party));
        when(partyReviewRepository.findByPartyId(7L)).thenReturn(List.of());

        List<PartyReviewDTO.Response> responses = partyReviewService.getReviewsByParty(7L, 200L);

        assertThat(responses).isEmpty();
        verify(partyReviewRepository).findByPartyId(7L);
    }

    @Test
    @DisplayName("getReviewsByParty treats pending applicants or outsiders as not found")
    void getReviewsByParty_rejectsNonParticipantBeforeLoadingReviews() {
        when(partyRepository.findAccessibleByIdAndParticipantId(7L, 300L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partyReviewService.getReviewsByParty(7L, 300L))
                .isInstanceOf(PartyNotFoundException.class);

        verify(partyReviewRepository, never()).findByPartyId(7L);
    }
}
