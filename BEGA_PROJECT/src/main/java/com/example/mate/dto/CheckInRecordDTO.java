package com.example.mate.dto;

import com.example.mate.entity.CheckInRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
        @NotNull(message = "파티 ID는 필수입니다.")
        private Long partyId;

        @NotBlank(message = "체크인 위치는 필수입니다.")
        @Size(max = 100, message = "체크인 위치는 100자 이하여야 합니다.")
        private String location;

        @Size(max = 128, message = "QR 세션 ID는 128자 이하여야 합니다.")
        private String qrSessionId;

        @Pattern(regexp = "^\\d{4}$", message = "수동 체크인 코드는 4자리 숫자여야 합니다.")
        private String manualCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QrSessionRequest {
        @NotNull(message = "파티 ID는 필수입니다.")
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
        private String manualCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long partyId;
        private String userHandle;
        private String userName;
        private String location;
        private LocalDateTime checkedInAt;

        public static Response from(CheckInRecord record, String userHandle, String userName) {
            return Response.builder()
                    .id(record.getId())
                    .partyId(record.getPartyId())
                    .userHandle(userHandle)
                    .userName(userName)
                    .location(record.getLocation())
                    .checkedInAt(record.getCheckedInAt())
                    .build();
        }
    }
}
