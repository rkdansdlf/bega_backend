package com.example.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.service.port.ContentModerationDecision;
import com.example.common.service.port.ContentModerationPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AIModerationServiceTest {

    private final ContentModerationPort moderationPort = mock(ContentModerationPort.class);
    private final AIModerationService moderationService = new AIModerationService(moderationPort);

    @BeforeEach
    void setUpRules() {
        ReflectionTestUtils.setField(moderationService, "highRiskKeywordsRaw", "죽어,병신");
        ReflectionTestUtils.setField(moderationService, "spamKeywordsRaw", "광고,홍보,오픈채팅");
        ReflectionTestUtils.setField(moderationService, "spamUrlThreshold", 3);
        ReflectionTestUtils.setField(moderationService, "repeatCharThreshold", 8);
        ReflectionTestUtils.setField(moderationService, "spamMediumThreshold", 2);
        ReflectionTestUtils.setField(moderationService, "spamBlockThreshold", 3);
    }

    @Test
    void checkContentMergesAllowedRuleResultWithModelDecision() {
        when(moderationPort.moderate("오늘 경기 정말 재밌었어요!"))
                .thenReturn(Optional.of(new ContentModerationDecision(
                        "SAFE", "", "ALLOW", "MODEL", "LOW")));

        AIModerationService.ModerationResult result =
                moderationService.checkContent("오늘 경기 정말 재밌었어요!");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.decisionSource()).isEqualTo("MODEL");
    }

    @Test
    void checkContentUsesExistingRuleFallbackWhenAdapterIsUnavailable() {
        when(moderationPort.moderate("병신")).thenReturn(Optional.empty());

        AIModerationService.ModerationResult result = moderationService.checkContent("병신");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.category()).isEqualTo("INAPPROPRIATE");
        assertThat(result.decisionSource()).isEqualTo("FALLBACK");
    }
}
