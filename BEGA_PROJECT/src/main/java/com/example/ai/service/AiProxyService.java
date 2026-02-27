package com.example.ai.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiProxyService {

    private static final Duration AI_PROXY_TIMEOUT = Duration.ofSeconds(60);
    private static final Set<String> PASS_THROUGH_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.RETRY_AFTER,
            "X-Accel-Buffering");

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    @Value("${ai.internal-token:}")
    private String aiInternalToken;

    private final WebClient.Builder webClientBuilder;

    public ProxyByteResponse forwardJson(String uri, String payload) {
        WebClient.RequestHeadersSpec<?> request = client().post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::applyInternalAuth)
                .bodyValue(payload);
        return executeByteRequest(request);
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

            return executeByteRequest(request);
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

        ProxyStreamResponse response = request.exchangeToMono(clientResponse -> {
            HttpStatusCode statusCode = clientResponse.statusCode();
            HttpHeaders headers = filterResponseHeaders(clientResponse.headers().asHttpHeaders());
            if (statusCode.is2xxSuccessful()) {
                return Mono.just(new ProxyStreamResponse(
                        statusCode,
                        headers,
                        clientResponse.bodyToFlux(DataBuffer.class),
                        null));
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
            return clientResponse.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(ignored -> new ProxyStreamResponse(
                            statusCode,
                            headers,
                            Flux.empty(),
                            buildStandardizedErrorBody(statusCode)));
        })
                .block(AI_PROXY_TIMEOUT);

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

    private ProxyByteResponse executeByteRequest(WebClient.RequestHeadersSpec<?> request) {
        ProxyByteResponse response = request.exchangeToMono(clientResponse -> clientResponse
                .bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .map(body -> {
                    HttpStatusCode statusCode = clientResponse.statusCode();
                    HttpHeaders headers = filterResponseHeaders(clientResponse.headers().asHttpHeaders());
                    if (!statusCode.is2xxSuccessful()) {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        body = buildStandardizedErrorBody(statusCode);
                    }
                    return new ProxyByteResponse(statusCode, headers, body);
                }))
                .block(AI_PROXY_TIMEOUT);

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI upstream response was empty");
        }
        return response;
    }

    private WebClient client() {
        return webClientBuilder.baseUrl(aiServiceUrl).build();
    }

    private void applyInternalAuth(HttpHeaders headers) {
        if (!StringUtils.hasText(aiInternalToken)) {
            log.error("ai.internal-token is not configured.");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI internal authentication is not configured");
        }
        headers.set("X-Internal-Api-Key", aiInternalToken);
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
