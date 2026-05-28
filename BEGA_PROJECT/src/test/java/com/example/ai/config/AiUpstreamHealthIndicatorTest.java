package com.example.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

class AiUpstreamHealthIndicatorTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void healthIsUpWhenAiHealthReturnsOk() throws Exception {
        server = startServer("/health", exchange -> writeResponse(exchange, 200, "{\"status\":\"ok\"}"));
        AiUpstreamHealthIndicator indicator = newIndicator(
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", 200);
    }

    @Test
    void healthIsDownWhenConnectionFails() {
        AiUpstreamHealthIndicator indicator = newIndicator("http://localhost:1", Duration.ofSeconds(1));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("target", "http://localhost:1");
    }

    @Test
    void healthIsDownWhenAiHealthTimesOut() throws Exception {
        server = startServer("/health", exchange -> {
            try {
                Thread.sleep(250);
                writeResponse(exchange, 200, "{\"status\":\"ok\"}");
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Timeout closes the client side; status is asserted through the health result.
            }
        });
        AiUpstreamHealthIndicator indicator = newIndicator(
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofMillis(50));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    private AiUpstreamHealthIndicator newIndicator(String serviceUrl, Duration timeout) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        AiServiceSettings settings = new AiServiceSettings(environment, serviceUrl, "test-token");
        return new AiUpstreamHealthIndicator(settings, WebClient.builder(), timeout);
    }

    private HttpServer startServer(String path, HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext(path, handler);
        httpServer.start();
        return httpServer;
    }

    private void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
