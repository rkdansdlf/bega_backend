package com.example.ai.service;

import com.example.ai.exception.AiProxyException;
import com.example.ai.config.AiServiceSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import reactor.core.publisher.Flux;

class AiProxyServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardJsonPreservesUnauthorizedStatusAndInternalTokenHeader() throws Exception {
        AtomicReference<String> internalToken = new AtomicReference<>();
        server = startServer("/ai/chat/completion", exchange -> {
            internalToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            writeResponse(exchange, 401, "unauthorized");
        });

        AiProxyService service = newServiceForProfile(Duration.ofSeconds(5), "test-token", "prod");

        AiProxyService.ProxyByteResponse response = service.forwardJson("/ai/chat/completion", "{\"test\":true}");

        assertThat(response.status().value()).isEqualTo(401);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .contains("\"code\":\"AI_UPSTREAM_UNAUTHORIZED\"")
                .contains("\"message\":\"AI 서비스 인증에 실패했습니다.\"");
        assertThat(internalToken.get()).isEqualTo("test-token");
    }

    @Test
    void forwardJsonStreamPreservesUpstreamUnavailableStatus() throws Exception {
        server = startServer("/ai/coach/analyze", exchange -> writeResponse(exchange, 503, "down"));

        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token");

        AiProxyService.ProxyStreamResponse response = service.forwardJsonStream("/ai/coach/analyze", "{\"test\":true}");

        assertThat(response.status().value()).isEqualTo(503);
        assertThat(new String(response.errorBody(), StandardCharsets.UTF_8))
                .contains("\"code\":\"AI_UPSTREAM_UNAVAILABLE\"")
                .contains("\"message\":\"AI 서비스가 현재 사용할 수 없습니다.\"");
    }

    @Test
    void forwardJsonStreamPreservesSuccessfulSseBody() throws Exception {
        server = startServer("/ai/coach/analyze", exchange -> writeResponse(
                exchange,
                200,
                "event: meta\ndata: {\"request_mode\":\"manual_detail\"}\n\nevent: done\ndata: [DONE]\n\n",
                "text/event-stream"));

        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token");

        AiProxyService.ProxyStreamResponse response = service.forwardJsonStream("/ai/coach/analyze", "{\"test\":true}");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        service.writeStream(response.bodyFlux(), outputStream);

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(outputStream.toString(StandardCharsets.UTF_8))
                .contains("event: meta")
                .contains("event: done")
                .contains("data: [DONE]");
    }

    @Test
    void forwardJsonStreamForwardsNegotiatedVersionAndPreservesResponseHeaderAndBody() throws Exception {
        AtomicReference<String> requestVersion = new AtomicReference<>();
        String sseBody = "event: chat.message.delta\ndata: {\"version\":2,\"type\":\"chat.message.delta\",\"data\":{\"delta\":\"안녕\"}}\n\n";
        server = startServer("/ai/chat/stream", exchange -> {
            requestVersion.set(exchange.getRequestHeaders().getFirst("X-AI-Event-Version"));
            exchange.getResponseHeaders().add("X-AI-Event-Version", "2");
            writeResponse(exchange, 200, sseBody, "text/event-stream");
        });

        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token");

        AiProxyService.ProxyStreamResponse response = service.forwardJsonStream(
                "/ai/chat/stream",
                "{\"test\":true}",
                "2");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.writeStream(response.bodyFlux(), outputStream);

        assertThat(requestVersion.get()).isEqualTo("2");
        assertThat(response.headers().getFirst("X-AI-Event-Version")).isEqualTo("2");
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo(sseBody);
    }

    @Test
    void forwardJsonStreamNormalizesCanonicalUnsupportedVersion() throws Exception {
        String body = """
                {"code":"AI_EVENT_VERSION_UNSUPPORTED","message":"지원하지 않는 AI 이벤트 버전입니다.","detail":null,"retryable":false,"retry_after_seconds":null,"supported_versions":["1","2"]}
                """;
        server = startServer("/ai/chat/stream", exchange -> {
            exchange.getResponseHeaders().add("X-AI-Event-Version", "2");
            writeResponse(exchange, 406, body, "application/json");
        });

        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token");

        AiProxyService.ProxyStreamResponse response = service.forwardJsonStream(
                "/ai/chat/stream",
                "{\"test\":true}",
                "3");

        assertThat(response.status().value()).isEqualTo(406);
        assertThat(response.headers().getFirst("X-AI-Event-Version")).isEqualTo("2");
        assertThat(response.headers().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(json(response.errorBody())).isEqualTo(json(body));
    }

    @Test
    void forwardJsonStreamAdaptsLegacyWrappedUnsupportedVersion() throws Exception {
        server = startServer("/ai/chat/stream", exchange -> writeResponse(
                exchange,
                406,
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"supported_versions\":[\"1\",\"2\"]}}",
                "application/json"));

        AiProxyService.ProxyStreamResponse response = newService(Duration.ofSeconds(5), "stream-token")
                .forwardJsonStream("/ai/chat/stream", "{}", "3");

        assertThat(json(response.errorBody()).get("code").asText())
                .isEqualTo("AI_EVENT_VERSION_UNSUPPORTED");
        assertThat(json(response.errorBody()).get("detail").isNull()).isTrue();
    }

    @Test
    void forwardJsonStreamRejectsMalformedCanonicalUnsupportedVersionBodies() throws Exception {
        List<String> malformedBodies = List.of(
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"2\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"2\"],\"secret\":\"secret-bearing\"}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":42,\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"2\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":\"secret-bearing\",\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"2\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":true,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"2\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":0,\"supported_versions\":[\"1\",\"2\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"2\",\"1\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"1\",\"2\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"3\"]}",
                "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",2]}");
        AtomicInteger responseIndex = new AtomicInteger();
        server = startServer("/ai/chat/stream", exchange -> writeResponse(
                exchange,
                406,
                malformedBodies.get(responseIndex.getAndIncrement()),
                "application/json"));
        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token");

        for (String malformedBody : malformedBodies) {
            AiProxyService.ProxyStreamResponse response = service.forwardJsonStream("/ai/chat/stream", "{}", "3");
            JsonNode error = json(response.errorBody());

            assertThat(response.status().value()).isEqualTo(406);
            assertThat(error.get("code").asText()).as(malformedBody).isEqualTo("AI_UPSTREAM_BAD_REQUEST");
            assertThat(error.get("supported_versions").isEmpty()).as(malformedBody).isTrue();
            assertThat(new String(response.errorBody(), StandardCharsets.UTF_8)).doesNotContain("secret-bearing");
        }
    }

    @Test
    void forwardJsonStreamRejectsMalformedLegacyUnsupportedVersionBodies() throws Exception {
        List<String> malformedBodies = List.of(
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\"}}",
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"supported_versions\":[\"2\",\"1\"]}}",
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"supported_versions\":[\"1\",\"1\",\"2\"]}}",
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"supported_versions\":[\"1\",\"3\"]}}",
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"supported_versions\":[\"1\",2]}}",
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"supported_versions\":[\"1\",\"2\"],\"message\":\"secret-bearing\"}}",
                "{\"detail\":{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"supported_versions\":[\"1\",\"2\"]},\"extra\":true}");
        AtomicInteger responseIndex = new AtomicInteger();
        server = startServer("/ai/chat/stream", exchange -> writeResponse(
                exchange,
                406,
                malformedBodies.get(responseIndex.getAndIncrement()),
                "application/json"));
        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token");

        for (String malformedBody : malformedBodies) {
            AiProxyService.ProxyStreamResponse response = service.forwardJsonStream("/ai/chat/stream", "{}", "3");
            JsonNode error = json(response.errorBody());

            assertThat(error.get("code").asText()).as(malformedBody).isEqualTo("AI_UPSTREAM_BAD_REQUEST");
            assertThat(error.get("supported_versions").isEmpty()).as(malformedBody).isTrue();
            assertThat(new String(response.errorBody(), StandardCharsets.UTF_8)).doesNotContain("secret-bearing");
        }
    }

    @Test
    void forwardJsonStreamRejectsUnsupportedVersionWithWrongContentType() throws Exception {
        String body = "{\"code\":\"AI_EVENT_VERSION_UNSUPPORTED\",\"message\":\"지원하지 않는 AI 이벤트 버전입니다.\",\"detail\":null,\"retryable\":false,\"retry_after_seconds\":null,\"supported_versions\":[\"1\",\"2\"]}";
        server = startServer("/ai/chat/stream", exchange -> writeResponse(exchange, 406, body, "text/plain"));

        AiProxyService.ProxyStreamResponse response = newService(Duration.ofSeconds(5), "stream-token")
                .forwardJsonStream("/ai/chat/stream", "{}", "3");

        assertThat(json(response.errorBody()).get("code").asText()).isEqualTo("AI_UPSTREAM_BAD_REQUEST");
        assertThat(json(response.errorBody()).get("supported_versions").isEmpty()).isTrue();
    }

    @Test
    void forwardJsonStreamDoesNotExposeMalformedUpstreamBody() throws Exception {
        server = startServer("/ai/coach/analyze", exchange -> writeResponse(
                exchange, 503, "secret-bearing upstream body", "text/plain"));

        AiProxyService.ProxyStreamResponse response = newService(Duration.ofSeconds(5), "stream-token")
                .forwardJsonStream("/ai/coach/analyze", "{}");
        String body = new String(response.errorBody(), StandardCharsets.UTF_8);

        assertThat(body).contains("\"code\":\"AI_UPSTREAM_UNAVAILABLE\"");
        assertThat(body).contains("\"retryable\":true");
        assertThat(body).doesNotContain("secret-bearing");
    }

    @Test
    void forwardJsonStreamPreservesIntegerRetryAfterInCanonicalError() throws Exception {
        server = startServer("/ai/chat/stream", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "00037");
            writeResponse(exchange, 429, "upstream rate limit", "text/plain");
        });

        AiProxyService.ProxyStreamResponse response = newService(Duration.ofSeconds(5), "stream-token")
                .forwardJsonStream("/ai/chat/stream", "{}");

        assertThat(response.headers().getFirst("Retry-After")).isEqualTo("37");
        assertThat(json(response.errorBody()).get("retry_after_seconds").asLong()).isEqualTo(37L);
    }

    @Test
    void forwardJsonStreamIgnoresDateFormRetryAfterInCanonicalError() throws Exception {
        server = startServer("/ai/chat/stream", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "Wed, 21 Oct 2015 07:28:00 GMT");
            writeResponse(exchange, 429, "upstream rate limit", "text/plain");
        });

        AiProxyService.ProxyStreamResponse response = newService(Duration.ofSeconds(5), "stream-token")
                .forwardJsonStream("/ai/chat/stream", "{}");

        assertThat(json(response.errorBody()).get("retry_after_seconds").isNull()).isTrue();
        assertThat(response.headers().getFirst("Retry-After")).isNull();
    }

    @Test
    void forwardJsonStreamRemovesMalformedNegativeAndOverflowRetryAfter() throws Exception {
        List<String> invalidValues = List.of("not-a-number", "-1", "Wed, 21 Oct 2015 07:28:00 GMT", "9223372036854775808");
        AtomicInteger responseIndex = new AtomicInteger();
        server = startServer("/ai/chat/stream", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", invalidValues.get(responseIndex.getAndIncrement()));
            writeResponse(exchange, 429, "upstream rate limit", "text/plain");
        });
        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token");

        for (String invalidValue : invalidValues) {
            AiProxyService.ProxyStreamResponse response = service.forwardJsonStream("/ai/chat/stream", "{}");

            assertThat(response.headers().getFirst("Retry-After")).as(invalidValue).isNull();
            assertThat(json(response.errorBody()).get("retry_after_seconds").isNull()).as(invalidValue).isTrue();
        }
    }

    @Test
    void forwardJsonRecordsUpstreamRequestMetric() throws Exception {
        server = startServer("/ai/chat/completion", exchange -> writeResponse(exchange, 200, "{\"ok\":true}"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiProxyService service = newService(
                Duration.ofSeconds(5),
                "metric-token",
                new AiProxyMonitoringMetricsService(meterRegistry));

        AiProxyService.ProxyByteResponse response = service.forwardJson("/ai/chat/completion", "{\"test\":true}");

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(meterRegistry.get(AiProxyMonitoringMetricsService.UPSTREAM_REQUEST_DURATION_METRIC)
                .tag("endpoint", "chat_completion")
                .tag("mode", "byte")
                .tag("status", "2xx")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void forwardJsonStreamRecordsUpstreamErrorMetric() throws Exception {
        server = startServer("/ai/coach/analyze", exchange -> writeResponse(exchange, 503, "down"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiProxyService service = newService(
                Duration.ofSeconds(5),
                "stream-token",
                new AiProxyMonitoringMetricsService(meterRegistry));

        AiProxyService.ProxyStreamResponse response = service.forwardJsonStream("/ai/coach/analyze", "{\"test\":true}");

        assertThat(response.status().value()).isEqualTo(503);
        assertThat(meterRegistry.get(AiProxyMonitoringMetricsService.UPSTREAM_REQUEST_DURATION_METRIC)
                .tag("endpoint", "coach_analyze")
                .tag("mode", "stream_header")
                .tag("status", "5xx")
                .tag("result", "upstream_error")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void forwardJsonRetriesWithLocalDevFallbackTokenOnUnauthorized() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> firstToken = new AtomicReference<>();
        AtomicReference<String> secondToken = new AtomicReference<>();
        server = startServer("/ai/chat/completion", exchange -> {
            int currentAttempt = requestCount.incrementAndGet();
            String header = exchange.getRequestHeaders().getFirst("X-Internal-Api-Key");
            if (currentAttempt == 1) {
                firstToken.set(header);
                writeResponse(exchange, 401, "unauthorized");
                return;
            }

            secondToken.set(header);
            writeResponse(exchange, 200, "{\"ok\":true}");
        });

        AiProxyService service = newService(Duration.ofSeconds(5), "mismatched-env-token");

        AiProxyService.ProxyByteResponse response = service.forwardJson("/ai/chat/completion", "{\"test\":true}");

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(firstToken.get()).isEqualTo("mismatched-env-token");
        assertThat(secondToken.get()).isEqualTo("local-dev-ai-internal-token");
    }

    @Test
    void forwardJsonStreamRetriesWithLocalDevFallbackTokenOnUnauthorized() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> firstToken = new AtomicReference<>();
        AtomicReference<String> secondToken = new AtomicReference<>();
        server = startServer("/ai/coach/analyze", exchange -> {
            int currentAttempt = requestCount.incrementAndGet();
            String header = exchange.getRequestHeaders().getFirst("X-Internal-Api-Key");
            if (currentAttempt == 1) {
                firstToken.set(header);
                writeResponse(exchange, 401, "unauthorized");
                return;
            }

            secondToken.set(header);
            writeResponse(exchange, 200, "event: done\ndata: [DONE]\n\n");
        });

        AiProxyService service = newService(Duration.ofSeconds(5), "mismatched-env-token");

        AiProxyService.ProxyStreamResponse response = service.forwardJsonStream("/ai/coach/analyze", "{\"test\":true}");

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(firstToken.get()).isEqualTo("mismatched-env-token");
        assertThat(secondToken.get()).isEqualTo("local-dev-ai-internal-token");
    }

    @Test
    void forwardJsonStreamPreservesEventVersionAcrossInternalTokenRetry() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> firstVersion = new AtomicReference<>();
        AtomicReference<String> secondVersion = new AtomicReference<>();
        server = startServer("/ai/coach/analyze", exchange -> {
            int currentAttempt = requestCount.incrementAndGet();
            String version = exchange.getRequestHeaders().getFirst("X-AI-Event-Version");
            if (currentAttempt == 1) {
                firstVersion.set(version);
                writeResponse(exchange, 401, "unauthorized");
                return;
            }
            secondVersion.set(version);
            writeResponse(exchange, 200, "event: stream.done\ndata: {\"version\":2,\"type\":\"stream.done\",\"data\":{\"reason\":\"completed\"}}\n\n");
        });

        AiProxyService service = newService(Duration.ofSeconds(5), "mismatched-env-token");

        AiProxyService.ProxyStreamResponse response = service.forwardJsonStream(
                "/ai/coach/analyze",
                "{\"test\":true}",
                "2");

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(firstVersion.get()).isEqualTo("2");
        assertThat(secondVersion.get()).isEqualTo("2");
    }

    @Test
    void forwardGetPreservesUnauthorizedStatusAndInternalTokenHeader() throws Exception {
        AtomicReference<String> internalToken = new AtomicReference<>();
        server = startServer("/ai/release-decision/presets", exchange -> {
            internalToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            writeResponse(exchange, 401, "unauthorized");
        });

        AiProxyService service = newServiceForProfile(Duration.ofSeconds(5), "preset-token", "prod");

        AiProxyService.ProxyByteResponse response = service.forwardGet("/ai/release-decision/presets");

        assertThat(response.status().value()).isEqualTo(401);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .contains("\"code\":\"AI_UPSTREAM_UNAUTHORIZED\"")
                .contains("\"message\":\"AI 서비스 인증에 실패했습니다.\"");
        assertThat(internalToken.get()).isEqualTo("preset-token");
    }

    @Test
    void forwardJsonTimeoutMapsToGatewayTimeout() throws Exception {
        server = startServer("/ai/chat/completion", exchange -> {
            try {
                Thread.sleep(250);
                writeResponse(exchange, 200, "{\"ok\":true}");
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Client timeout closes the socket; the status mapping is what matters.
            }
        });

        AiProxyService service = newService(Duration.ofMillis(50), "slow-token");

        assertThatThrownBy(() -> service.forwardJson("/ai/chat/completion", "{\"test\":true}"))
                .isInstanceOf(AiProxyException.class)
                .satisfies(throwable -> {
                    AiProxyException ex = (AiProxyException) throwable;
                    assertThat(ex.getStatus().value()).isEqualTo(504);
                    assertThat(ex.getCode()).isEqualTo("AI_UPSTREAM_TIMEOUT");
                    assertThat(ex.getMessage()).isEqualTo("AI 응답 시간이 초과되었습니다.");
                });
    }

    @Test
    void forwardJsonUsesRequestTimeoutWhenStreamHeaderTimeoutIsShort() throws Exception {
        server = startServer("/ai/chat/completion", exchange -> {
            try {
                Thread.sleep(120);
                writeResponse(exchange, 200, "{\"ok\":true}");
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        });

        AiProxyService service = newService(
                Duration.ofSeconds(1),
                Duration.ofMillis(50),
                "slow-byte-token");

        AiProxyService.ProxyByteResponse response = service.forwardJson("/ai/chat/completion", "{\"test\":true}");

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    }

    @Test
    void forwardJsonStreamHeaderTimeoutMapsToGatewayTimeout() throws Exception {
        server = startServer("/ai/coach/analyze", exchange -> {
            try {
                Thread.sleep(250);
                writeResponse(exchange, 200, "event: done\ndata: [DONE]\n\n", "text/event-stream");
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Client timeout closes the socket; the status mapping is what matters.
            }
        });

        AiProxyService service = newService(
                Duration.ofSeconds(5),
                Duration.ofMillis(50),
                "slow-stream-token");

        assertThatThrownBy(() -> service.forwardJsonStream("/ai/coach/analyze", "{\"test\":true}"))
                .isInstanceOf(AiProxyException.class)
                .satisfies(throwable -> {
                    AiProxyException ex = (AiProxyException) throwable;
                    assertThat(ex.getStatus().value()).isEqualTo(504);
                    assertThat(ex.getCode()).isEqualTo("AI_UPSTREAM_TIMEOUT");
                    assertThat(ex.getMessage()).isEqualTo("AI 응답 시간이 초과되었습니다.");
                });
    }

    @Test
    void forwardJsonStreamConnectionFailureMapsToBadGateway() {
        AiProxyService service = newService(Duration.ofSeconds(1), "stream-token", "http://localhost:1");

        assertThatThrownBy(() -> service.forwardJsonStream("/ai/coach/analyze", "{\"test\":true}"))
                .isInstanceOf(AiProxyException.class)
                .satisfies(throwable -> {
                    AiProxyException ex = (AiProxyException) throwable;
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("AI_UPSTREAM_CONNECTION_FAILED");
                    assertThat(ex.getMessage()).isEqualTo("AI 서비스 연결에 실패했습니다.");
                });
    }

    @Test
    void writeStreamStopsCleanlyOnBrokenPipe() {
        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token", "http://localhost:1");
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        Flux<DataBuffer> bodyFlux = Flux.just(
                bufferFactory.wrap("event: message\ndata: {\"delta\":\"안녕\"}\n\n".getBytes(StandardCharsets.UTF_8)));

        OutputStream brokenPipeStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        assertThatCode(() -> service.writeStream(bodyFlux, brokenPipeStream))
                .doesNotThrowAnyException();
    }

    @Test
    void writeStreamPropagatesNonDisconnectIOException() {
        AiProxyService service = newService(Duration.ofSeconds(5), "stream-token", "http://localhost:1");
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        Flux<DataBuffer> bodyFlux = Flux.just(
                bufferFactory.wrap("event: message\ndata: {\"delta\":\"안녕\"}\n\n".getBytes(StandardCharsets.UTF_8)));

        OutputStream failingStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };

        assertThatThrownBy(() -> service.writeStream(bodyFlux, failingStream))
                .isInstanceOf(IOException.class)
                .hasMessage("disk full");
    }

    @Test
    void clientIsBuiltOnceAndReusedAcrossCalls() throws Exception {
        server = startServer("/ai/chat/completion", exchange -> writeResponse(exchange, 200, "ok"));

        WebClient.Builder spyBuilder = Mockito.spy(WebClient.builder());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        AiServiceSettings settings = new AiServiceSettings(
                environment,
                "http://localhost:" + server.getAddress().getPort(),
                "reuse-token");
        AiProxyService service = new AiProxyService(settings, spyBuilder, Duration.ofSeconds(5));

        for (int i = 0; i < 5; i++) {
            service.forwardJson("/ai/chat/completion", "{}");
        }

        Mockito.verify(spyBuilder, Mockito.times(1)).baseUrl(Mockito.anyString());
        Mockito.verify(spyBuilder, Mockito.times(1)).clientConnector(Mockito.any());
    }

    @Test
    void shutdownConnectionProviderDisposesDedicatedPool() {
        AiProxyService service = newService(Duration.ofSeconds(1), "shutdown-token", "http://localhost:1");

        assertThatCode(service::shutdownConnectionProvider).doesNotThrowAnyException();
    }

    private AiProxyService newService(Duration timeout, String token) {
        if (server == null) {
            throw new IllegalStateException("test server is not initialized");
        }
        return newService(timeout, token, "http://localhost:" + server.getAddress().getPort());
    }

    private AiProxyService newService(Duration requestTimeout, Duration streamHeaderTimeout, String token) {
        if (server == null) {
            throw new IllegalStateException("test server is not initialized");
        }
        return newService(
                requestTimeout,
                streamHeaderTimeout,
                token,
                "http://localhost:" + server.getAddress().getPort());
    }

    private AiProxyService newService(
            Duration timeout,
            String token,
            AiProxyMonitoringMetricsService metricsService) {
        if (server == null) {
            throw new IllegalStateException("test server is not initialized");
        }
        return newService(timeout, token, "http://localhost:" + server.getAddress().getPort(), metricsService, "dev");
    }

    private AiProxyService newServiceForProfile(Duration timeout, String token, String profile) {
        if (server == null) {
            throw new IllegalStateException("test server is not initialized");
        }
        return newService(timeout, token, "http://localhost:" + server.getAddress().getPort(), profile);
    }

    private AiProxyService newService(Duration timeout, String token, String serviceUrl) {
        return newService(timeout, token, serviceUrl, "dev");
    }

    private AiProxyService newService(Duration timeout, String token, String serviceUrl, String... activeProfiles) {
        return newService(timeout, token, serviceUrl, AiProxyMonitoringMetricsService.noop(), activeProfiles);
    }

    private AiProxyService newService(
            Duration timeout,
            String token,
            String serviceUrl,
            AiProxyMonitoringMetricsService metricsService,
            String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        AiServiceSettings settings = new AiServiceSettings(
                environment,
                serviceUrl,
                token);
        return new AiProxyService(settings, WebClient.builder(), metricsService, timeout);
    }

    private AiProxyService newService(
            Duration requestTimeout,
            Duration streamHeaderTimeout,
            String token,
            String serviceUrl) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        AiServiceSettings settings = new AiServiceSettings(
                environment,
                serviceUrl,
                token);
        return new AiProxyService(
                settings,
                WebClient.builder(),
                AiProxyMonitoringMetricsService.noop(),
                requestTimeout,
                streamHeaderTimeout);
    }

    private HttpServer startServer(String path, HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext(path, handler);
        httpServer.start();
        return httpServer;
    }

    private void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        writeResponse(exchange, status, body, "text/plain; charset=utf-8");
    }

    private void writeResponse(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private JsonNode json(byte[] value) throws IOException {
        return objectMapper.readTree(value);
    }

    private JsonNode json(String value) throws IOException {
        return objectMapper.readTree(value);
    }
}
