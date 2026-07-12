package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CheerFeedServicePostTypeTest {

    @Test
    void lightweightFallbackPreservesEntityPostType() {
        CheerFeedService service = new CheerFeedService(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        CheerPost post = CheerPost.builder()
                .id(51L)
                .content("notice")
                .postType(PostType.NOTICE)
                .build();

        try {
            PostLightweightSummaryRes result = ReflectionTestUtils.invokeMethod(
                    service, "buildFallbackPostLightweightSummary", post);

            assertThat(result).isNotNull();
            assertThat(result.postType()).isEqualTo("NOTICE");
            assertThat(result.linkedContent()).isNull();
        } finally {
            ReflectionTestUtils.invokeMethod(service, "shutdownExecutor");
        }
    }
}
