package com.example.common.contract;

import com.example.admin.dto.AdminPostDto;
import com.example.homepage.OffseasonMovementDto;
import com.example.leaderboard.dto.ScoreResultDto;
import com.example.mate.dto.PartyApplicationDTO;
import com.example.mypage.dto.DeviceSessionDto;
import com.example.notification.dto.NotificationDTO;
import com.example.prediction.GameInningScoreDto;
import com.example.prediction.MatchDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanIsFieldSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void notificationResponseShouldKeepIsRead() throws Exception {
        NotificationDTO.Response dto = NotificationDTO.Response.builder()
                .id(1L)
                .isRead(true)
                .build();

        assertHasIsKeyAndNoLegacy(toJson(dto), "isRead", "read");
    }

    @Test
    void adminPostShouldKeepIsHot() throws Exception {
        AdminPostDto dto = AdminPostDto.builder()
                .id(1L)
                .isHot(true)
                .build();

        assertHasIsKeyAndNoLegacy(toJson(dto), "isHot", "hot");
    }

    @Test
    void gameInningScoreShouldKeepIsExtra() throws Exception {
        GameInningScoreDto dto = GameInningScoreDto.builder()
                .inning(1)
                .isExtra(true)
                .build();

        assertHasIsKeyAndNoLegacy(toJson(dto), "isExtra", "extra");
    }

    @Test
    void matchShouldKeepIsDummy() throws Exception {
        MatchDto dto = MatchDto.builder()
                .gameId("G1")
                .isDummy(true)
                .build();

        assertHasIsKeyAndNoLegacy(toJson(dto), "isDummy", "dummy");
    }

    @Test
    void deviceSessionShouldKeepIsCurrentAndIsRevoked() throws Exception {
        DeviceSessionDto dto = DeviceSessionDto.builder()
                .id("session-1")
                .isCurrent(true)
                .isRevoked(false)
                .build();

        JsonNode json = toJson(dto);
        assertHasIsKeyAndNoLegacy(json, "isCurrent", "current");
        assertHasIsKeyAndNoLegacy(json, "isRevoked", "revoked");
    }

    @Test
    void partyApplicationResponseShouldKeepIsFlags() throws Exception {
        PartyApplicationDTO.Response dto = PartyApplicationDTO.Response.builder()
                .id(1L)
                .isPaid(true)
                .isApproved(false)
                .isRejected(false)
                .build();

        JsonNode json = toJson(dto);
        assertHasIsKeyAndNoLegacy(json, "isPaid", "paid");
        assertHasIsKeyAndNoLegacy(json, "isApproved", "approved");
        assertHasIsKeyAndNoLegacy(json, "isRejected", "rejected");
    }

    @Test
    void offseasonMovementShouldKeepIsBigEvent() throws Exception {
        OffseasonMovementDto dto = OffseasonMovementDto.builder()
                .id(1L)
                .isBigEvent(true)
                .build();

        assertHasIsKeyAndNoLegacy(toJson(dto), "isBigEvent", "bigEvent");
    }

    @Test
    void scoreResultShouldKeepIsNewMaxStreak() throws Exception {
        ScoreResultDto dto = ScoreResultDto.builder()
                .userId(1L)
                .isNewMaxStreak(true)
                .build();

        assertHasIsKeyAndNoLegacy(toJson(dto), "isNewMaxStreak", "newMaxStreak");
    }

    private JsonNode toJson(Object value) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(value));
    }

    private void assertHasIsKeyAndNoLegacy(JsonNode json, String isKey, String legacyKey) {
        assertTrue(json.has(isKey));
        assertFalse(json.has(legacyKey));
    }
}
