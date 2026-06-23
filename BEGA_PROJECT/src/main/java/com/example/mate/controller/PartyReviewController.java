package com.example.mate.controller;

// Force IDE re-index

import com.example.common.web.AuthenticatedUserIds;
import com.example.mate.dto.PartyReviewDTO;
import com.example.mate.service.PartyReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class PartyReviewController {

    private final PartyReviewService partyReviewService;

    /**
     * 리뷰 작성
     */
    @Operation(summary = "Mate review create")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = PartyReviewDTO.Response.class)))
    @PostMapping
    public ResponseEntity<PartyReviewDTO.Response> createReview(
            @RequestBody PartyReviewDTO.Request request,
            @AuthenticationPrincipal Long userId) {
        PartyReviewDTO.Response response = partyReviewService.createReview(request, AuthenticatedUserIds.require(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 파티별 리뷰 조회
     */
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<PartyReviewDTO.Response>> getReviewsByParty(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        List<PartyReviewDTO.Response> reviews = partyReviewService.getReviewsByParty(partyId, AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(reviews);
    }
}
