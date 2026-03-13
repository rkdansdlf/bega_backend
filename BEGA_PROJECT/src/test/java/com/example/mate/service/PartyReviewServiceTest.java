package com.example.mate.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.UserService;
import com.example.mate.dto.PartyReviewDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PartyReview;
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
import static org.mockito.ArgumentMatchers.any;
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
        when(userService.getUserIdByHandle("@guest")).thenReturn(200L);
        when(userService.findUserById(100L)).thenReturn(UserEntity.builder().id(100L).handle("@host").build());
        when(userService.findUserById(200L)).thenReturn(UserEntity.builder().id(200L).handle("@guest").build());
        when(partyRepository.findById(7L)).thenReturn(Optional.of(party));
        when(partyApplicationRepository.findByPartyIdAndApplicantId(7L, 100L)).thenReturn(Optional.empty());
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
    @DisplayName("getReviewsByParty includes reviewer and reviewee handles")
    void getReviewsByParty_includesHandles() {
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

        List<PartyReviewDTO.Response> responses = partyReviewService.getReviewsByParty(7L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getReviewerHandle()).isEqualTo("@host");
        assertThat(responses.get(0).getRevieweeHandle()).isEqualTo("@guest");
    }
}
