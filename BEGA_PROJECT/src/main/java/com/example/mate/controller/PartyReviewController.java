package com.example.mate.controller;

// Force IDE re-index

import com.example.mate.dto.PartyReviewDTO;
import com.example.mate.service.PartyReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    @PostMapping
    public ResponseEntity<PartyReviewDTO.Response> createReview(@RequestBody PartyReviewDTO.Request request) {
        PartyReviewDTO.Response response = partyReviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * 파티별 리뷰 조회
     */
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<PartyReviewDTO.Response>> getReviewsByParty(@PathVariable Long partyId) {
        List<PartyReviewDTO.Response> reviews = partyReviewService.getReviewsByParty(partyId);
        return ResponseEntity.ok(reviews);
    }

    /**
     * 사용자의 평균 평점 조회
     */
    @GetMapping("/user/{userId}/average")
    public ResponseEntity<Double> getAverageRating(@PathVariable Long userId) {
        Double averageRating = partyReviewService.getAverageRating(userId);
        return ResponseEntity.ok(averageRating);
    }
}
