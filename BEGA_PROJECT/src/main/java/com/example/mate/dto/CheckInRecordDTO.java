package com.example.mate.dto;

import com.example.mate.entity.CheckInRecord;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDateTime;

public class CheckInRecordDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private Long partyId;
        private Long userId;
        private String location;
        private String qrSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QrSessionRequest {
        private Long partyId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QrSessionResponse {
        private String sessionId;
        private Long partyId;
        private Instant expiresAt;
        private String checkinUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long partyId;
        private Long userId;
        private String userName; // Added userName
        private String location;
        private LocalDateTime checkedInAt;

        public static Response from(CheckInRecord record, String userName) {
            return Response.builder()
                    .id(record.getId())
                    .partyId(record.getPartyId())
                    .userId(record.getUserId())
                    .userName(userName)
                    .location(record.getLocation())
                    .checkedInAt(record.getCheckedInAt())
                    .build();
        }
    }
}
