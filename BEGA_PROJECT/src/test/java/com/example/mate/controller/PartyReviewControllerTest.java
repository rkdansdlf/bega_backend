package com.example.mate.controller;

import com.example.mate.dto.PartyReviewDTO;
import com.example.mate.service.PartyReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyReviewControllerTest {

    @Mock
    private PartyReviewService partyReviewService;

    @InjectMocks
    private PartyReviewController controller;

    private final Principal principal = () -> "42";

    @Test
    @DisplayName("리뷰 작성 시 201을 반환한다")
    void createReview_returns201() {
        PartyReviewDTO.Request req = PartyReviewDTO.Request.builder().partyId(5L).rating(5).build();
        PartyReviewDTO.Response resp = PartyReviewDTO.Response.builder().id(1L).build();
        when(partyReviewService.createReview(req, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.createReview(req, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("파티별 리뷰 목록을 조회한다")
    void getReviewsByParty_returnsList() {
        PartyReviewDTO.Response resp = PartyReviewDTO.Response.builder().id(1L).build();
        when(partyReviewService.getReviewsByParty(5L)).thenReturn(List.of(resp));

        ResponseEntity<List<PartyReviewDTO.Response>> result = controller.getReviewsByParty(5L);

        assertThat(result.getBody()).hasSize(1);
        verify(partyReviewService).getReviewsByParty(5L);
    }
}
