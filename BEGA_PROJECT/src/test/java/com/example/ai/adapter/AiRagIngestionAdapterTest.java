package com.example.ai.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.ai.config.AiServiceSettings;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AiRagIngestionAdapterTest {

    @Test
    void triggerUsesCanonicalEndpointAndInternalToken() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        when(settings.buildUrl("/ai/ingest/run")).thenReturn("http://ai/ai/ingest/run");
        when(settings.getResolvedInternalToken()).thenReturn("internal-token");
        server.expect(requestTo("http://ai/ai/ingest/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Internal-Api-Key", "internal-token"))
                .andRespond(withSuccess("accepted", MediaType.TEXT_PLAIN));
        AiRagIngestionAdapter adapter = new AiRagIngestionAdapter(settings, restTemplate);

        adapter.trigger();

        server.verify();
    }

    @Test
    void triggerPreservesMissingConfigurationError() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        when(settings.buildUrl("/ai/ingest/run")).thenReturn("");
        when(settings.getResolvedInternalToken()).thenReturn("internal-token");
        AiRagIngestionAdapter adapter = new AiRagIngestionAdapter(settings, new RestTemplate());

        assertThatThrownBy(adapter::trigger)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI service URL is not configured");
    }
}
