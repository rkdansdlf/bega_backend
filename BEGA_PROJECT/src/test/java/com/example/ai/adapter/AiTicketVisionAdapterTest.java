package com.example.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ai.config.AiServiceSettings;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.exception.TicketAnalysisException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
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

    @ParameterizedTest
    @MethodSource("upstreamErrorMappings")
    void analyzePreservesUpstreamErrorMapping(
            HttpStatus upstreamStatus,
            HttpStatus expectedStatus,
            String expectedMessage) {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(upstreamStatus).build());
        AiTicketVisionAdapter adapter = new AiTicketVisionAdapter(
                settings(),
                WebClient.builder().exchangeFunction(exchange));

        assertThatThrownBy(() -> adapter.analyze(validImage()))
                .isInstanceOfSatisfying(TicketAnalysisException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(expectedStatus);
                    assertThat(exception.getMessage()).isEqualTo(expectedMessage);
                });
    }

    @Test
    void analyzePreservesMissingConfigurationErrors() {
        AiServiceSettings missingUrl = mock(AiServiceSettings.class);
        when(missingUrl.getResolvedServiceUrl()).thenReturn("");
        AiTicketVisionAdapter missingUrlAdapter = new AiTicketVisionAdapter(
                missingUrl,
                WebClient.builder());

        assertThatThrownBy(() -> missingUrlAdapter.analyze(validImage()))
                .isInstanceOfSatisfying(TicketAnalysisException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("티켓 분석 서비스 주소가 설정되지 않았습니다.");
                });

        AiServiceSettings missingToken = mock(AiServiceSettings.class);
        when(missingToken.getResolvedServiceUrl()).thenReturn("http://ai.internal");
        when(missingToken.getResolvedInternalToken()).thenReturn("");
        AiTicketVisionAdapter missingTokenAdapter = new AiTicketVisionAdapter(
                missingToken,
                WebClient.builder());

        assertThatThrownBy(() -> missingTokenAdapter.analyze(validImage()))
                .isInstanceOfSatisfying(TicketAnalysisException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage())
                            .isEqualTo("티켓 분석 서비스 인증 설정이 누락되었습니다. 관리자에게 문의해주세요.");
                });
    }

    @Test
    void analyzePreservesUnreadableAndEmptyResponseErrors() throws IOException {
        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.getBytes()).thenThrow(new IOException("unreadable"));
        AiTicketVisionAdapter adapter = new AiTicketVisionAdapter(settings(), WebClient.builder());

        assertThatThrownBy(() -> adapter.analyze(unreadable))
                .isInstanceOfSatisfying(TicketAnalysisException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("티켓 이미지를 읽는 중 오류가 발생했습니다.");
                });

        ExchangeFunction emptyExchange = request -> Mono.just(ClientResponse.create(HttpStatus.OK).build());
        AiTicketVisionAdapter emptyResponseAdapter = new AiTicketVisionAdapter(
                settings(),
                WebClient.builder().exchangeFunction(emptyExchange));

        assertThatThrownBy(() -> emptyResponseAdapter.analyze(validImage()))
                .isInstanceOfSatisfying(TicketAnalysisException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getMessage())
                            .isEqualTo("티켓 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                });
    }

    private static Stream<Arguments> upstreamErrorMappings() {
        return Stream.of(
                Arguments.of(
                        HttpStatus.UNAUTHORIZED,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "티켓 분석 서비스 인증에 실패했습니다. 서버 설정을 확인해주세요."),
                Arguments.of(
                        HttpStatus.FORBIDDEN,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "티켓 분석 서비스 인증에 실패했습니다. 서버 설정을 확인해주세요."),
                Arguments.of(
                        HttpStatus.CONTENT_TOO_LARGE,
                        HttpStatus.BAD_REQUEST,
                        "이미지 파일 크기가 너무 큽니다. (최대 5MB)"),
                Arguments.of(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        HttpStatus.BAD_REQUEST,
                        "지원되지 않는 이미지 형식입니다. JPG, PNG, WEBP 파일만 가능합니다."),
                Arguments.of(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        HttpStatus.BAD_REQUEST,
                        "티켓 분석 요청이 올바르지 않습니다. 다른 파일로 다시 시도해주세요."),
                Arguments.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        HttpStatus.BAD_GATEWAY,
                        "티켓 분석 AI 서버에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
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
