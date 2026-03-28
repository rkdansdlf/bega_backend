package com.example.common.service;

import com.example.ai.config.AiServiceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class AIModerationServiceTest {

    @Mock
    private AiServiceSettings aiServiceSettings;

    private AIModerationService moderationService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        moderationService = new AIModerationService(aiServiceSettings, restTemplate);
        ReflectionTestUtils.setField(moderationService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(moderationService, "highRiskKeywordsRaw", "죽어,병신");
        ReflectionTestUtils.setField(moderationService, "spamKeywordsRaw", "광고,홍보,오픈채팅");
        ReflectionTestUtils.setField(moderationService, "spamUrlThreshold", 3);
        ReflectionTestUtils.setField(moderationService, "repeatCharThreshold", 8);
        ReflectionTestUtils.setField(moderationService, "spamMediumThreshold", 2);
        ReflectionTestUtils.setField(moderationService, "spamBlockThreshold", 3);
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("moderation client should send internal auth header to AI service")
    void checkContent_sendsInternalTokenHeader() {
        when(aiServiceSettings.buildUrl("/moderation/safety-check")).thenReturn("http://ai/moderation/safety-check");
        when(aiServiceSettings.getResolvedInternalToken()).thenReturn("expected-token");

        mockServer.expect(requestTo("http://ai/moderation/safety-check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Internal-Api-Key", "expected-token"))
                .andRespond(withSuccess(
                        """
                        {"category":"SAFE","reason":"","action":"ALLOW","decisionSource":"MODEL","riskLevel":"LOW"}
                        """,
                        MediaType.APPLICATION_JSON));

        AIModerationService.ModerationResult result = moderationService.checkContent("오늘 경기 정말 재밌었어요!");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.decisionSource()).isEqualTo("MODEL");
        mockServer.verify();
    }

    @Test
    @DisplayName("missing internal token should fall back to rule evaluation")
    void checkContent_missingInternalTokenFallsBackToRuleEvaluation() {
        when(aiServiceSettings.buildUrl("/moderation/safety-check")).thenReturn("http://ai/moderation/safety-check");
        when(aiServiceSettings.getResolvedInternalToken()).thenReturn("");

        AIModerationService.ModerationResult result = moderationService.checkContent("병신");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.category()).isEqualTo("INAPPROPRIATE");
        assertThat(result.decisionSource()).isEqualTo("FALLBACK");
    }
}
