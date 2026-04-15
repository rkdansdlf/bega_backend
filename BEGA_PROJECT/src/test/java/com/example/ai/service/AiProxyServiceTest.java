package com.example.ai.service;

import com.example.ai.exception.AiProxyException;
import com.example.ai.config.AiServiceSettings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import reactor.core.publisher.Flux;

class AiProxyServiceTest {

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

    private AiProxyService newService(Duration timeout, String token) {
        if (server == null) {
            throw new IllegalStateException("test server is not initialized");
        }
        return newService(timeout, token, "http://localhost:" + server.getAddress().getPort());
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
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        AiServiceSettings settings = new AiServiceSettings(
                environment,
                serviceUrl,
                token);
        return new AiProxyService(settings, WebClient.builder(), timeout);
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
}
