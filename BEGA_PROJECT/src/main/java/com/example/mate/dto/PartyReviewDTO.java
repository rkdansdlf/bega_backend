package com.example.mate.dto;

// Force IDE re-index

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
        private String revieweeHandle;
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
        private String reviewerHandle;
        private String revieweeHandle;
        private Integer rating;
        private String comment;
        private Instant createdAt;

        public static Response from(PartyReview review) {
            return Response.builder()
                    .id(review.getId())
                    .partyId(review.getPartyId())
                    .reviewerHandle(null)
                    .revieweeHandle(null)
                    .rating(review.getRating())
                    .comment(review.getComment())
                    .createdAt(review.getCreatedAt())
                    .build();
        }
    }
}
