package com.example.cheerboard.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cheer DTO serialization tests")
class CheerDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("check-in linked content exposes only the public preview")
    void checkinLinkedContent_serializesPublicPreviewOnly() throws Exception {
        LinkedContentRes linkedContent = LinkedContentRes.availableCheckin(new CheckinLinkedContentRes(
                LocalDate.of(2026, 7, 13),
                "LG",
                "KIA",
                "LG",
                "잠실",
                true));

        String json = objectMapper.writeValueAsString(linkedContent);
        JsonNode checkinNode = objectMapper.readTree(json);

        assertThat(checkinNode).isEqualTo(objectMapper.readTree("""
                {
                  "kind": "CHECKIN",
                  "available": true,
                  "unavailableReason": null,
                  "checkin": {
                    "gameDate": "2026-07-13",
                    "homeTeam": "LG",
                    "awayTeam": "KIA",
                    "cheeringTeam": "LG",
                    "stadium": "잠실",
                    "verified": true
                  },
                  "recruitment": null
                }
                """));
        assertThat(json).doesNotContain("memo", "photo", "ticket", "seatRow", "diaryId");
    }

    @Test
    @DisplayName("recruitment linked content exposes only the public preview")
    void recruitmentLinkedContent_serializesPublicPreviewOnly() throws Exception {
        LinkedContentRes linkedContent = LinkedContentRes.availableRecruitment(new RecruitmentLinkedContentRes(
                44L,
                LocalDate.of(2026, 7, 13),
                LocalTime.of(18, 30),
                "LG",
                "KIA",
                "잠실",
                "블루석",
                2,
                4,
                "RECRUITING",
                true,
                "같이 응원해요",
                10000,
                20000,
                5000));

        String json = objectMapper.writeValueAsString(linkedContent);
        JsonNode recruitmentNode = objectMapper.readTree(json);

        assertThat(recruitmentNode).isEqualTo(objectMapper.readTree("""
                {
                  "kind": "RECRUITMENT",
                  "available": true,
                  "unavailableReason": null,
                  "checkin": null,
                  "recruitment": {
                    "partyId": 44,
                    "gameDate": "2026-07-13",
                    "gameTime": "18:30:00",
                    "homeTeam": "LG",
                    "awayTeam": "KIA",
                    "stadium": "잠실",
                    "section": "블루석",
                    "currentParticipants": 2,
                    "maxParticipants": 4,
                    "status": "RECRUITING",
                    "recruiting": true,
                    "description": "같이 응원해요",
                    "price": 10000,
                    "ticketPrice": 20000,
                    "reservationDepositAmount": 5000
                  }
                }
                """));
        assertThat(json).doesNotContain("members", "reservationNumber", "ticketImageUrl");
    }

    @Test
    @DisplayName("recruitment game time documents the same string shape emitted by Jackson")
    void recruitmentLinkedContent_documentsGameTimeAsString() throws Exception {
        Schema schema = RecruitmentLinkedContentRes.class
                .getMethod("gameTime")
                .getAnnotation(Schema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("string");
        assertThat(schema.format()).isEqualTo("time");
        assertThat(schema.example()).isEqualTo("18:30:00");
    }

    @Test
    @DisplayName("linked content variants reject every invalid availability and payload combination")
    void linkedContent_enforcesVariantBoundary() throws Exception {
        CheckinLinkedContentRes checkin = new CheckinLinkedContentRes(
                LocalDate.of(2026, 7, 13), "LG", "KIA", "LG", "잠실", true);
        RecruitmentLinkedContentRes recruitment = new RecruitmentLinkedContentRes(
                44L, LocalDate.of(2026, 7, 13), LocalTime.of(18, 30), "LG", "KIA", "잠실", "블루석",
                2, 4, "RECRUITING", true, "같이 응원해요", 10000, 20000, 5000);

        assertThatThrownBy(() -> new LinkedContentRes(
                LinkedContentKind.CHECKIN, true, null, checkin, recruitment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkedContentRes(
                LinkedContentKind.CHECKIN, true, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkedContentRes(
                LinkedContentKind.CHECKIN, true, null, null, recruitment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkedContentRes(
                LinkedContentKind.CHECKIN, true, LinkedContentUnavailableReason.SOURCE_MISSING, checkin, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkedContentRes(
                LinkedContentKind.CHECKIN, false, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LinkedContentRes(
                LinkedContentKind.CHECKIN, false, LinkedContentUnavailableReason.SOURCE_MISSING, checkin, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkedContentRes(
                LinkedContentKind.RECRUITMENT, false, LinkedContentUnavailableReason.SOURCE_MISSING,
                null, recruitment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkedContentRes(
                null, false, LinkedContentUnavailableReason.SOURCE_MISSING, null, null))
                .isInstanceOf(NullPointerException.class);

        LinkedContentRes unavailable = LinkedContentRes.unavailable(
                LinkedContentKind.CHECKIN,
                LinkedContentUnavailableReason.MANUAL_BASEBALL_DATA_REQUIRED);
        JsonNode unavailableNode = objectMapper.readTree(objectMapper.writeValueAsString(unavailable));

        assertThat(unavailableNode.path("available").asBoolean()).isFalse();
        assertThat(unavailableNode.path("unavailableReason").asText())
                .isEqualTo("MANUAL_BASEBALL_DATA_REQUIRED");
        assertThat(unavailableNode.path("checkin").isNull()).isTrue();
        assertThat(unavailableNode.path("recruitment").isNull()).isTrue();
    }

    @Test
    @DisplayName("post summary should not serialize authorId")
    void postSummary_doesNotSerializeAuthorId() throws Exception {
        String json = objectMapper.writeValueAsString(PostSummaryRes.of(
                1L,
                "LG",
                "LG 트윈스",
                "LG",
                "#C30452",
                "내용",
                "작성자",
                "@author",
                "https://example.com/profile.jpg",
                "LG",
                Instant.parse("2026-03-10T00:00:00Z"),
                2,
                3,
                1,
                false,
                4,
                false,
                false,
                false,
                0,
                false,
                "NORMAL",
                List.of()));

        assertThat(json).contains("\"authorHandle\":\"@author\"");
        assertThat(json).doesNotContain("authorId");
    }

    @Test
    @DisplayName("post detail should not serialize authorId or authorEmail")
    void postDetail_doesNotSerializeAuthorIdentityFields() throws Exception {
        String json = objectMapper.writeValueAsString(PostDetailRes.of(
                1L,
                "LG",
                "LG 트윈스",
                "LG",
                "#C30452",
                "내용",
                "작성자",
                "@author",
                "https://example.com/profile.jpg",
                Instant.parse("2026-03-10T00:00:00Z"),
                2,
                3,
                1,
                false,
                false,
                false,
                List.of(),
                4,
                0,
                false,
                "NORMAL"));

        assertThat(json).contains("\"authorHandle\":\"@author\"");
        assertThat(json).doesNotContain("authorId");
        assertThat(json).doesNotContain("authorEmail");
    }

    @Test
    @DisplayName("lightweight summary should not serialize authorId")
    void postLightweightSummary_doesNotSerializeAuthorId() throws Exception {
        String json = objectMapper.writeValueAsString(PostLightweightSummaryRes.of(
                1L,
                "긴 내용",
                null,
                3,
                4,
                Instant.parse("2026-03-10T00:00:00Z"),
                "@author",
                "https://example.com/profile.jpg"));

        assertThat(json).contains("\"authorNickname\":\"@author\"");
        assertThat(json).doesNotContain("authorId");
    }

    @Test
    @DisplayName("deleted embedded placeholders do not fabricate an unknown post type")
    void embeddedPostDeletedPlaceholder_doesNotFabricatePostType() {
        EmbeddedPostDto placeholder = EmbeddedPostDto.deletedPlaceholder(99L);

        assertThat(placeholder.id()).isEqualTo(99L);
        assertThat(placeholder.deleted()).isTrue();
        assertThat(placeholder.postType()).isNull();
        assertThat(placeholder.linkedContent()).isNull();
    }

    @Test
    @DisplayName("comment response should not serialize authorEmail")
    void comment_doesNotSerializeAuthorEmail() throws Exception {
        String json = objectMapper.writeValueAsString(new CommentRes(
                1L,
                "작성자",
                "LG",
                "https://example.com/profile.jpg",
                "@author",
                "댓글",
                Instant.parse("2026-03-10T00:00:00Z"),
                0,
                false,
                List.of()));

        assertThat(json).contains("\"authorHandle\":\"@author\"");
        assertThat(json).doesNotContain("authorEmail");
    }
}
