package com.example.ai.service;

import com.example.ai.config.AiServiceSettings;
import com.example.ai.exception.AiProxyException;
import com.example.common.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AiProxyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
            throw new AiProxyException(HttpStatus.BAD_REQUEST, "AI_PROXY_INVALID_UPLOAD_PAYLOAD", "업로드 파일을 읽을 수 없습니다.");
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
        } catch (WebClientRequestException e) {
            throw mapRequestFailure(uri, e);
        } catch (IllegalStateException e) {
            throw mapBlockingFailure(uri, e);
        }

        if (response == null) {
            throw new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_EMPTY_RESPONSE", "AI 응답이 비어 있습니다.");
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
        } catch (WebClientRequestException e) {
            throw mapRequestFailure(uri, e);
        } catch (IllegalStateException e) {
            throw mapBlockingFailure(uri, e);
        }

        if (response == null) {
            throw new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_EMPTY_RESPONSE", "AI 응답이 비어 있습니다.");
        }
        return response;
    }

    private WebClient client() {
        String aiServiceUrl = aiServiceSettings.getResolvedServiceUrl();
        if (!StringUtils.hasText(aiServiceUrl)) {
            log.error("AI service URL is not configured.");
            throw new AiProxyException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_URL_NOT_CONFIGURED", "AI 서비스 주소가 설정되지 않았습니다.");
        }

        try {
            return webClientBuilder.baseUrl(aiServiceUrl).build();
        } catch (IllegalArgumentException e) {
            log.error("AI service URL is invalid. url={}", aiServiceUrl, e);
            throw new AiProxyException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_URL_INVALID", "AI 서비스 주소 설정이 올바르지 않습니다.");
        }
    }

    private void applyInternalAuth(HttpHeaders headers) {
        String aiInternalToken = aiServiceSettings.getResolvedInternalToken();
        if (!StringUtils.hasText(aiInternalToken)) {
            log.error("ai.internal-token is not configured.");
            throw new AiProxyException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_INTERNAL_AUTH_MISCONFIGURED",
                    "AI 내부 인증 설정이 누락되었습니다.");
        }
        headers.set("X-Internal-Api-Key", aiInternalToken);
    }

    private AiProxyException mapRequestFailure(String uri, WebClientRequestException e) {
        log.error("AI upstream connection failed. uri={} message={}", uri, e.getMessage(), e);
        return new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_CONNECTION_FAILED", "AI 서비스 연결에 실패했습니다.");
    }

    private RuntimeException mapBlockingFailure(String uri, IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
            log.error("AI upstream request timed out. uri={} timeout={}s", uri, requestTimeout.toSeconds(), e);
            return new AiProxyException(HttpStatus.GATEWAY_TIMEOUT, "AI_UPSTREAM_TIMEOUT", "AI 응답 시간이 초과되었습니다.");
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
        AiUpstreamError error = resolveUpstreamError(statusCode);
        try {
            return OBJECT_MAPPER.writeValueAsBytes(ApiResponse.error(error.code(), error.message()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AI proxy error response. status={}", statusCode.value(), e);
            return fallbackErrorJson(error);
        }
    }

    private AiUpstreamError resolveUpstreamError(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
            return new AiUpstreamError("AI_UPSTREAM_UNAUTHORIZED", "AI 서비스 인증에 실패했습니다.");
        }
        if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            return new AiUpstreamError("AI_UPSTREAM_FORBIDDEN", "AI 서비스 접근이 거부되었습니다.");
        }
        if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new AiUpstreamError("AI_UPSTREAM_RATE_LIMITED", "AI 서비스 요청 한도를 초과했습니다.");
        }
        if (statusCode.is4xxClientError()) {
            return new AiUpstreamError("AI_UPSTREAM_BAD_REQUEST", "AI 서비스가 요청을 처리할 수 없습니다.");
        }
        return new AiUpstreamError("AI_UPSTREAM_UNAVAILABLE", "AI 서비스가 현재 사용할 수 없습니다.");
    }

    private byte[] fallbackErrorJson(AiUpstreamError error) {
        String payload = "{\"success\":false,\"code\":\"" + error.code() + "\",\"message\":\"" + error.message() + "\"}";
        return payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public record ProxyByteResponse(HttpStatusCode status, HttpHeaders headers, byte[] body) {
    }

    public record ProxyStreamResponse(
            HttpStatusCode status,
            HttpHeaders headers,
            Flux<DataBuffer> bodyFlux,
            byte[] errorBody) {
    }

    private record AiUpstreamError(String code, String message) {
    }
}
