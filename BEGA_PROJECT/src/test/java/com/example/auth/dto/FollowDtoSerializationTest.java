package com.example.auth.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void followCountResponseShouldSerializeIsFollowedByMeFieldName() throws Exception {
        FollowCountResponse dto = FollowCountResponse.builder()
                .followerCount(1L)
                .followingCount(2L)
                .isFollowedByMe(true)
                .notifyNewPosts(false)
                .blockedByMe(false)
                .blockingMe(false)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertTrue(json.has("isFollowedByMe"));
        assertFalse(json.has("followedByMe"));
        assertTrue(json.get("isFollowedByMe").asBoolean());
    }

    @Test
    void userFollowSummaryShouldSerializeIsFollowedByMeFieldName() throws Exception {
        UserFollowSummaryDto dto = UserFollowSummaryDto.builder()
                .id(1L)
                .handle("@tester")
                .name("tester")
                .profileImageUrl(null)
                .favoriteTeam(null)
                .isFollowedByMe(true)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertTrue(json.has("isFollowedByMe"));
        assertFalse(json.has("followedByMe"));
        assertTrue(json.get("isFollowedByMe").asBoolean());
    }
}
