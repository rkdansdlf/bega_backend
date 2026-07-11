package com.example.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.ai.config.AiServiceSettings;
import com.example.common.service.port.ContentModerationDecision;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AiContentModerationAdapterTest {

    @Test
    void moderateSendsInternalTokenAndMapsModelDecision() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        when(settings.buildUrl("/moderation/safety-check"))
                .thenReturn("http://ai/moderation/safety-check");
        when(settings.getResolvedInternalToken()).thenReturn("expected-token");
        server.expect(requestTo("http://ai/moderation/safety-check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Internal-Api-Key", "expected-token"))
                .andRespond(withSuccess(
                        """
                        {"category":"SAFE","reason":"","action":"ALLOW","decisionSource":"MODEL","riskLevel":"LOW"}
                        """,
                        MediaType.APPLICATION_JSON));
        AiContentModerationAdapter adapter = new AiContentModerationAdapter(settings, restTemplate);

        Optional<ContentModerationDecision> result = adapter.moderate("오늘 경기 정말 재밌었어요!");

        assertThat(result).contains(new ContentModerationDecision(
                "SAFE", "", "ALLOW", "MODEL", "LOW"));
        server.verify();
    }

    @Test
    void moderateReturnsEmptyWhenInternalTokenIsMissing() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        when(settings.buildUrl("/moderation/safety-check"))
                .thenReturn("http://ai/moderation/safety-check");
        when(settings.getResolvedInternalToken()).thenReturn("");
        AiContentModerationAdapter adapter = new AiContentModerationAdapter(settings, new RestTemplate());

        assertThat(adapter.moderate("병신")).isEmpty();
    }
}
