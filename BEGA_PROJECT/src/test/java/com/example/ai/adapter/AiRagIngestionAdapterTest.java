package com.example.ai.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.example.ai.config.AiServiceSettings;
import com.example.ai.ingest.AiIngestRunRequest;
import com.example.ai.ingest.AiIngestRunStatus;
import com.example.ai.ingest.AiIngestRunStatusResponse;
import com.example.ai.ingest.AiIngestRunSubmission;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpServerErrorException;

class AiRagIngestionAdapterTest {

    @Test
    void submitUsesTypedPayloadCanonicalEndpointAndInternalToken() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        when(settings.buildUrl("/ai/ingest/run")).thenReturn("http://ai/ai/ingest/run");
        when(settings.getResolvedInternalToken()).thenReturn("internal-token");
        server.expect(requestTo("http://ai/ai/ingest/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Internal-Api-Key", "internal-token"))
                .andExpect(content().json("""
                        {
                          "tables": ["game", "game_metadata", "game_summary"],
                          "season_year": 2026,
                          "mode": "INCREMENTAL",
                          "trigger_source": "BACKEND_SCHEDULED"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "run_id": "44444444-4444-4444-8444-444444444444",
                          "status": "QUEUED",
                          "deduplicated": false
                        }
                        """, MediaType.APPLICATION_JSON));
        AiRagIngestionAdapter adapter = new AiRagIngestionAdapter(settings, restTemplate);

        AiIngestRunSubmission result = adapter.submit(new AiIngestRunRequest(
                List.of("game", "game_metadata", "game_summary"),
                2026,
                "INCREMENTAL",
                "BACKEND_SCHEDULED"));

        assertThat(result.runId()).isEqualTo(UUID.fromString("44444444-4444-4444-8444-444444444444"));
        assertThat(result.status()).isEqualTo(AiIngestRunStatus.QUEUED);
        assertThat(result.deduplicated()).isFalse();
        server.verify();
    }

    @Test
    void getStatusDeserializesManualDataRequiredContract() {
        UUID runId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        AiServiceSettings settings = mock(AiServiceSettings.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        when(settings.buildUrl("/ai/ingest/runs/" + runId))
                .thenReturn("http://ai/ai/ingest/runs/" + runId);
        when(settings.getResolvedInternalToken()).thenReturn("internal-token");
        server.expect(requestTo("http://ai/ai/ingest/runs/" + runId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "internal-token"))
                .andRespond(withSuccess("""
                        {
                          "run_id": "44444444-4444-4444-8444-444444444444",
                          "status": "MANUAL_BASEBALL_DATA_REQUIRED",
                          "trigger_source": "BACKEND_SCHEDULED",
                          "requested_at": "2026-07-15T04:30:00Z",
                          "started_at": "2026-07-15T04:30:01Z",
                          "finished_at": "2026-07-15T04:30:05Z",
                          "recovery_attempts": 0,
                          "tables": {},
                          "error": {
                            "code": "MANUAL_BASEBALL_DATA_REQUIRED",
                            "operator_message": "Operator verified game_date is required."
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        AiRagIngestionAdapter adapter = new AiRagIngestionAdapter(settings, restTemplate);

        AiIngestRunStatusResponse result = adapter.getStatus(runId);

        assertThat(result.status()).isEqualTo(AiIngestRunStatus.MANUAL_BASEBALL_DATA_REQUIRED);
        assertThat(result.error()).containsEntry("code", "MANUAL_BASEBALL_DATA_REQUIRED");
        assertThat(result.error()).containsEntry(
                "operator_message", "Operator verified game_date is required.");
        server.verify();
    }

    @Test
    void triggerPreservesMissingConfigurationError() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        when(settings.buildUrl("/ai/ingest/run")).thenReturn("");
        when(settings.getResolvedInternalToken()).thenReturn("internal-token");
        AiRagIngestionAdapter adapter = new AiRagIngestionAdapter(settings, new RestTemplate());

        assertThatThrownBy(() -> adapter.submit(new AiIngestRunRequest(
                List.of("game"), 2026, "INCREMENTAL", "BACKEND_SCHEDULED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI service URL is not configured");
    }

    @Test
    void submitPreservesNonSuccessResponse() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        when(settings.buildUrl("/ai/ingest/run")).thenReturn("http://ai/ai/ingest/run");
        when(settings.getResolvedInternalToken()).thenReturn("internal-token");
        server.expect(requestTo("http://ai/ai/ingest/run"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        AiRagIngestionAdapter adapter = new AiRagIngestionAdapter(settings, restTemplate);

        assertThatThrownBy(() -> adapter.submit(new AiIngestRunRequest(
                List.of("game"), 2026, "INCREMENTAL", "BACKEND_SCHEDULED")))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);

        server.verify();
    }
}
