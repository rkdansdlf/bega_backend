package com.example.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ai.config.AiServiceSettings;
import com.example.kbo.dto.TicketInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AiTicketVisionAdapterTest {

    @Test
    void analyzeUsesCanonicalEndpointAndInternalToken() {
        AiServiceSettings settings = settings();
        List<String> paths = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        ExchangeFunction exchange = request -> {
            paths.add(request.url().getPath());
            tokens.add(request.headers().getFirst("X-Internal-Api-Key"));
            return Mono.just(jsonResponse(HttpStatus.OK));
        };
        AiTicketVisionAdapter adapter = new AiTicketVisionAdapter(
                settings,
                WebClient.builder().exchangeFunction(exchange));

        TicketInfo result = adapter.analyze(validImage());

        assertThat(result.getHomeTeam()).isEqualTo("LG");
        assertThat(paths).containsExactly("/ai/vision/ticket");
        assertThat(tokens).containsExactly("internal-token");
    }

    @Test
    void analyzeFallsBackToLegacyEndpointOnlyAfterCanonical404() {
        AiServiceSettings settings = settings();
        List<String> paths = new ArrayList<>();
        ExchangeFunction exchange = request -> {
            paths.add(request.url().getPath());
            if (paths.size() == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
            }
            return Mono.just(jsonResponse(HttpStatus.OK));
        };
        AiTicketVisionAdapter adapter = new AiTicketVisionAdapter(
                settings,
                WebClient.builder().exchangeFunction(exchange));

        TicketInfo result = adapter.analyze(validImage());

        assertThat(result.getAwayTeam()).isEqualTo("두산");
        assertThat(paths).containsExactly("/ai/vision/ticket", "/vision/ticket");
    }

    private AiServiceSettings settings() {
        AiServiceSettings settings = mock(AiServiceSettings.class);
        when(settings.getResolvedServiceUrl()).thenReturn("http://ai.internal");
        when(settings.getResolvedInternalToken()).thenReturn("internal-token");
        return settings;
    }

    private MockMultipartFile validImage() {
        return new MockMultipartFile(
                "file", "ticket.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1});
    }

    private ClientResponse jsonResponse(HttpStatus status) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"date\":\"2026-07-11\",\"homeTeam\":\"LG\",\"awayTeam\":\"두산\"}")
                .build();
    }
}
