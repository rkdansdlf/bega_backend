package com.example.cheerboard.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cheer DTO serialization tests")
class CheerDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
