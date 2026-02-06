package com.example.mate.dto;

import com.example.mate.entity.PartyReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class PartyReviewDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private Long partyId;
        private Long reviewerId;
        private Long revieweeId;
        private Integer rating; // 1-5
        private String comment; // 최대 200자
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long partyId;
        private Long reviewerId;
        private Long revieweeId;
        private Integer rating;
        private String comment;
        private Instant createdAt;

        public static Response from(PartyReview review) {
            return Response.builder()
                    .id(review.getId())
                    .partyId(review.getPartyId())
                    .reviewerId(review.getReviewerId())
                    .revieweeId(review.getRevieweeId())
                    .rating(review.getRating())
                    .comment(review.getComment())
                    .createdAt(review.getCreatedAt())
                    .build();
        }
    }
}
