package com.example.mate.dto;

import com.example.mate.entity.CheckInRecord;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PartyReview;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PartyDTO serialization tests")
class PartyDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("public party response should not serialize hostId")
    void publicResponse_doesNotSerializeHostId() throws Exception {
        PartyDTO.Response response = PartyDTO.Response.builder()
                .id(1L)
                .hostId(99L)
                .hostHandle("@host")
                .hostName("Host")
                .build();

        String json = objectMapper.writeValueAsString(PartyDTO.PublicResponse.from(response));

        assertThat(json).contains("\"hostHandle\":\"@host\"");
        assertThat(json).doesNotContain("hostId");
    }

    @Test
    @DisplayName("application response should not serialize applicantId")
    void applicationResponse_doesNotSerializeApplicantId() throws Exception {
        String json = objectMapper.writeValueAsString(PartyApplicationDTO.Response.from(
                PartyApplication.builder()
                        .id(7L)
                        .partyId(8L)
                        .applicantId(9L)
                        .applicantName("applicant")
                        .depositAmount(30000)
                        .paymentType(PartyApplication.PaymentType.FULL)
                        .isPaid(true)
                        .isApproved(false)
                        .isRejected(false)
                        .build()));

        assertThat(json).contains("\"applicantName\":\"applicant\"");
        assertThat(json).doesNotContain("applicantId");
    }

    @Test
    @DisplayName("check-in response should not serialize userId")
    void checkInResponse_doesNotSerializeUserId() throws Exception {
        String json = objectMapper.writeValueAsString(CheckInRecordDTO.Response.from(
                CheckInRecord.builder()
                        .id(3L)
                        .partyId(5L)
                        .userId(11L)
                        .location("잠실")
                        .checkedInAt(LocalDateTime.of(2026, 3, 9, 18, 30))
                        .build(),
                "@guest",
                "게스트"));

        assertThat(json).contains("\"userHandle\":\"@guest\"");
        assertThat(json).doesNotContain("userId");
    }

    @Test
    @DisplayName("review response should not serialize reviewerId or revieweeId")
    void reviewResponse_doesNotSerializeReviewerIds() throws Exception {
        String json = objectMapper.writeValueAsString(PartyReviewDTO.Response.from(
                PartyReview.builder()
                        .id(4L)
                        .partyId(6L)
                        .reviewerId(12L)
                        .revieweeId(13L)
                        .rating(5)
                        .comment("좋아요")
                        .build()));

        assertThat(json).contains("\"rating\":5");
        assertThat(json).doesNotContain("reviewerId");
        assertThat(json).doesNotContain("revieweeId");
    }
}
