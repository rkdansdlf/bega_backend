package com.example.ai.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import com.example.ai.config.AiServiceSettings;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiProxyService {

    private static final Set<String> PASS_THROUGH_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.RETRY_AFTER,
            "X-Accel-Buffering");

    private final AiServiceSettings aiServiceSettings;
    private final WebClient.Builder webClientBuilder;
    private final Duration requestTimeout;

    @Autowired
    public AiProxyService(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.request-timeout-seconds:180}") long requestTimeoutSeconds) {
        this(aiServiceSettings, webClientBuilder, Duration.ofSeconds(Math.max(30L, requestTimeoutSeconds)));
    }

    AiProxyService(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            Duration requestTimeout) {
        this.aiServiceSettings = aiServiceSettings;
        this.webClientBuilder = webClientBuilder;
        this.requestTimeout = requestTimeout;
    }

    public ProxyByteResponse forwardJson(String uri, String payload) {
        WebClient.RequestHeadersSpec<?> request = client().post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::applyInternalAuth)
                .bodyValue(payload);
        return executeByteRequest(uri, request);
    }

    public ProxyByteResponse forwardGet(String uri) {
        WebClient.RequestHeadersSpec<?> request = client().get()
                .uri(uri)
                .headers(this::applyInternalAuth);
        return executeByteRequest(uri, request);
    }

    public ProxyByteResponse forwardMultipart(String uri, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            WebClient.RequestHeadersSpec<?> request = client().post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(this::applyInternalAuth)
                    .body(BodyInserters.fromMultipartData(builder.build()));

            return executeByteRequest(uri, request);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload payload", e);
        }
    }

    public ProxyStreamResponse forwardJsonStream(String uri, String payload) {
        WebClient.RequestHeadersSpec<?> request = client().post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::applyInternalAuth)
                .bodyValue(payload);

        ProxyStreamResponse response;
        try {
            response = request.exchangeToMono(clientResponse -> {
                HttpStatusCode statusCode = clientResponse.statusCode();
                HttpHeaders headers = filterResponseHeaders(clientResponse.headers().asHttpHeaders());
                if (statusCode.is2xxSuccessful()) {
                    return Mono.just(new ProxyStreamResponse(
                            statusCode,
                            headers,
                            clientResponse.bodyToFlux(DataBuffer.class),
                            null));
                }
                log.warn("AI upstream returned status={} for stream uri={}", statusCode.value(), uri);
                headers.setContentType(MediaType.APPLICATION_JSON);
                return clientResponse.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(ignored -> new ProxyStreamResponse(
                                statusCode,
                                headers,
                                Flux.empty(),
                                buildStandardizedErrorBody(statusCode)));
            })
                    .block(requestTimeout);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (WebClientRequestException e) {
            throw mapRequestFailure(uri, e);
        } catch (IllegalStateException e) {
            throw mapBlockingFailure(uri, e);
        }

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI upstream response was empty");
        }
        return response;
    }

    public void writeStream(Flux<DataBuffer> bodyFlux, OutputStream outputStream) throws IOException {
        for (DataBuffer dataBuffer : bodyFlux.toIterable()) {
            try {
                byte[] chunk = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(chunk);
                outputStream.write(chunk);
                outputStream.flush();
            } finally {
                DataBufferUtils.release(dataBuffer);
            }
        }
    }

    private ProxyByteResponse executeByteRequest(String uri, WebClient.RequestHeadersSpec<?> request) {
        ProxyByteResponse response;
        try {
            response = request.exchangeToMono(clientResponse -> clientResponse
                    .bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(body -> {
                        HttpStatusCode statusCode = clientResponse.statusCode();
                        HttpHeaders headers = filterResponseHeaders(clientResponse.headers().asHttpHeaders());
                        if (!statusCode.is2xxSuccessful()) {
                            log.warn("AI upstream returned status={} for uri={}", statusCode.value(), uri);
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            body = buildStandardizedErrorBody(statusCode);
                        }
                        return new ProxyByteResponse(statusCode, headers, body);
                    }))
                    .block(requestTimeout);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (WebClientRequestException e) {
            throw mapRequestFailure(uri, e);
        } catch (IllegalStateException e) {
            throw mapBlockingFailure(uri, e);
        }

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI upstream response was empty");
        }
        return response;
    }

    private WebClient client() {
        String aiServiceUrl = aiServiceSettings.getResolvedServiceUrl();
        if (!StringUtils.hasText(aiServiceUrl)) {
            log.error("AI service URL is not configured.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service URL is not configured");
        }

        try {
            return webClientBuilder.baseUrl(aiServiceUrl).build();
        } catch (IllegalArgumentException e) {
            log.error("AI service URL is invalid. url={}", aiServiceUrl, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service URL is invalid", e);
        }
    }

    private void applyInternalAuth(HttpHeaders headers) {
        String aiInternalToken = aiServiceSettings.getResolvedInternalToken();
        if (!StringUtils.hasText(aiInternalToken)) {
            log.error("ai.internal-token is not configured.");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI internal authentication is not configured");
        }
        headers.set("X-Internal-Api-Key", aiInternalToken);
    }

    private ResponseStatusException mapRequestFailure(String uri, WebClientRequestException e) {
        log.error("AI upstream connection failed. uri={} message={}", uri, e.getMessage(), e);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI upstream connection failed", e);
    }

    private RuntimeException mapBlockingFailure(String uri, IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
            log.error("AI upstream request timed out. uri={} timeout={}s", uri, requestTimeout.toSeconds(), e);
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI upstream request timed out", e);
        }
        return e;
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders upstream) {
        HttpHeaders filtered = new HttpHeaders();
        upstream.forEach((name, values) -> {
            if (isPassThroughHeader(name)) {
                filtered.put(name, values);
            }
        });
        return filtered;
    }

    private boolean isPassThroughHeader(String headerName) {
        for (String allowedHeader : PASS_THROUGH_HEADERS) {
            if (allowedHeader.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    private byte[] buildStandardizedErrorBody(HttpStatusCode statusCode) {
        String message;
        if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
            message = "Unauthorized request to AI upstream.";
        } else if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            message = "Forbidden request to AI upstream.";
        } else if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            message = "AI upstream rate limit exceeded.";
        } else if (statusCode.is4xxClientError()) {
            message = "AI upstream rejected the request.";
        } else {
            message = "AI upstream is unavailable.";
        }

        String payload = String.format(
                "{\"success\":false,\"status\":%d,\"message\":\"%s\"}",
                statusCode.value(),
                message);
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    public record ProxyByteResponse(HttpStatusCode status, HttpHeaders headers, byte[] body) {
    }

    public record ProxyStreamResponse(
            HttpStatusCode status,
            HttpHeaders headers,
            Flux<DataBuffer> bodyFlux,
            byte[] errorBody) {
    }
}
