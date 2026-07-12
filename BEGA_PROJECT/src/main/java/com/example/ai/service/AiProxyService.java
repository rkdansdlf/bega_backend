package com.example.ai.service;

import com.example.ai.config.AiServiceSettings;
import com.example.ai.exception.AiProxyException;
import com.example.common.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Slf4j
@Service
public class AiProxyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONNECTION_PROVIDER_NAME = "ai-proxy";
    private static final int DEFAULT_MAX_CONNECTIONS = 40;
    private static final int DEFAULT_PENDING_ACQUIRE_MAX_COUNT = 64;
    private static final Duration DEFAULT_PENDING_ACQUIRE_TIMEOUT = Duration.ofMillis(500);
    private static final Duration DEFAULT_STREAM_HEADER_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> PASS_THROUGH_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.RETRY_AFTER,
            "X-Accel-Buffering");

    private final AiServiceSettings aiServiceSettings;
    private final WebClient.Builder webClientBuilder;
    private final Duration requestTimeout;
    private final Duration streamHeaderTimeout;
    private final ConnectionProvider connectionProvider;
    private final AiProxyMonitoringMetricsService metricsService;

    private volatile WebClient cachedClient;
    private volatile String cachedClientBaseUrl;

    @Autowired
    public AiProxyService(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            AiProxyMonitoringMetricsService metricsService,
            @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.request-timeout-seconds:180}") long requestTimeoutSeconds,
            @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.stream-header-timeout-seconds:30}") long streamHeaderTimeoutSeconds,
            @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.max-connections:40}") int maxConnections,
            @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.pending-acquire-timeout-ms:500}") long pendingAcquireTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.pending-acquire-max-count:64}") int pendingAcquireMaxCount) {
        this(
                aiServiceSettings,
                webClientBuilder,
                metricsService,
                Duration.ofSeconds(Math.max(30L, requestTimeoutSeconds)),
                Duration.ofSeconds(Math.max(5L, streamHeaderTimeoutSeconds)),
                maxConnections,
                Duration.ofMillis(Math.max(1L, pendingAcquireTimeoutMs)),
                pendingAcquireMaxCount);
    }

    AiProxyService(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            Duration requestTimeout) {
        this(
                aiServiceSettings,
                webClientBuilder,
                AiProxyMonitoringMetricsService.noop(),
                requestTimeout,
                requestTimeout,
                DEFAULT_MAX_CONNECTIONS,
                DEFAULT_PENDING_ACQUIRE_TIMEOUT,
                DEFAULT_PENDING_ACQUIRE_MAX_COUNT);
    }

    AiProxyService(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            AiProxyMonitoringMetricsService metricsService,
            Duration requestTimeout) {
        this(
                aiServiceSettings,
                webClientBuilder,
                metricsService,
                requestTimeout,
                requestTimeout,
                DEFAULT_MAX_CONNECTIONS,
                DEFAULT_PENDING_ACQUIRE_TIMEOUT,
                DEFAULT_PENDING_ACQUIRE_MAX_COUNT);
    }

    AiProxyService(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            AiProxyMonitoringMetricsService metricsService,
            Duration requestTimeout,
            Duration streamHeaderTimeout) {
        this(
                aiServiceSettings,
                webClientBuilder,
                metricsService,
                requestTimeout,
                streamHeaderTimeout,
                DEFAULT_MAX_CONNECTIONS,
                DEFAULT_PENDING_ACQUIRE_TIMEOUT,
                DEFAULT_PENDING_ACQUIRE_MAX_COUNT);
    }

    AiProxyService(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            AiProxyMonitoringMetricsService metricsService,
            Duration requestTimeout,
            Duration streamHeaderTimeout,
            int maxConnections,
            Duration pendingAcquireTimeout,
            int pendingAcquireMaxCount) {
        this.aiServiceSettings = aiServiceSettings;
        this.webClientBuilder = webClientBuilder;
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(30);
        this.streamHeaderTimeout = streamHeaderTimeout != null
                ? streamHeaderTimeout
                : DEFAULT_STREAM_HEADER_TIMEOUT;
        this.metricsService = metricsService != null ? metricsService : AiProxyMonitoringMetricsService.noop();
        this.connectionProvider = ConnectionProvider.builder(CONNECTION_PROVIDER_NAME)
                .maxConnections(Math.max(1, maxConnections))
                .pendingAcquireTimeout(pendingAcquireTimeout != null
                        ? pendingAcquireTimeout
                        : DEFAULT_PENDING_ACQUIRE_TIMEOUT)
                .pendingAcquireMaxCount(Math.max(0, pendingAcquireMaxCount))
                .metrics(true)
                .build();
    }

    @PreDestroy
    void shutdownConnectionProvider() {
        connectionProvider.disposeLater().block(Duration.ofSeconds(5));
    }

    public ProxyByteResponse forwardJson(String uri, String payload) {
        return executeByteRequest(uri, internalToken -> client().post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyInternalAuth(headers, internalToken))
                .bodyValue(payload));
    }

    public ProxyByteResponse forwardGet(String uri) {
        return executeByteRequest(uri, internalToken -> client().get()
                .uri(uri)
                .headers(headers -> applyInternalAuth(headers, internalToken)));
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
            return executeByteRequest(uri, internalToken -> client().post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> applyInternalAuth(headers, internalToken))
                    .body(BodyInserters.fromMultipartData(builder.build())));
        } catch (IOException e) {
            throw new AiProxyException(HttpStatus.BAD_REQUEST, "AI_PROXY_INVALID_UPLOAD_PAYLOAD", "업로드 파일을 읽을 수 없습니다.");
        }
    }

    public ProxyStreamResponse forwardJsonStream(String uri, String payload) {
        return executeStreamRequest(uri, internalToken -> client().post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyInternalAuth(headers, internalToken))
                .bodyValue(payload));
    }

    public void writeStream(Flux<DataBuffer> bodyFlux, OutputStream outputStream) throws IOException {
        for (DataBuffer dataBuffer : bodyFlux.toIterable()) {
            try {
                byte[] chunk = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(chunk);
                outputStream.write(chunk);
                outputStream.flush();
            } catch (IOException e) {
                if (isClientDisconnect(e)) {
                    log.debug("Downstream client disconnected while streaming AI response.");
                    return;
                }
                throw e;
            } finally {
                DataBufferUtils.release(dataBuffer);
            }
        }
    }

    private ProxyByteResponse executeByteRequest(
            String uri,
            Function<String, WebClient.RequestHeadersSpec<?>> requestFactory) {
        List<String> tokenCandidates = resolveInternalTokenCandidates();
        ProxyByteResponse lastResponse = null;

        for (int attemptIndex = 0; attemptIndex < tokenCandidates.size(); attemptIndex++) {
            lastResponse = exchangeByteRequest(uri, requestFactory.apply(tokenCandidates.get(attemptIndex)));
            if (lastResponse.status().value() != HttpStatus.UNAUTHORIZED.value() || attemptIndex == tokenCandidates.size() - 1) {
                return lastResponse;
            }
            log.warn("AI upstream rejected primary internal token for uri={}; retrying with local dev fallback token.", uri);
        }

        if (lastResponse == null) {
            throw new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_EMPTY_RESPONSE", "AI 응답이 비어 있습니다.");
        }
        return lastResponse;
    }

    private ProxyByteResponse exchangeByteRequest(String uri, WebClient.RequestHeadersSpec<?> request) {
        long startedAtNanos = System.nanoTime();
        Integer statusCodeValue = null;
        String result = "failure";
        try {
            ProxyByteResponse response = request.exchangeToMono(clientResponse -> clientResponse
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

            if (response == null) {
                throw new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_EMPTY_RESPONSE", "AI 응답이 비어 있습니다.");
            }
            statusCodeValue = response.status().value();
            result = resultForStatus(response.status());
            return response;
        } catch (WebClientRequestException e) {
            result = "connection_failure";
            throw mapRequestFailure(uri, e);
        } catch (IllegalStateException e) {
            RuntimeException mapped = mapBlockingFailure(uri, requestTimeout, e);
            result = resultForBlockingFailure(mapped);
            throw mapped;
        } finally {
            recordUpstreamRequest(uri, "byte", statusCodeValue, result, startedAtNanos);
        }
    }

    private ProxyStreamResponse executeStreamRequest(
            String uri,
            Function<String, WebClient.RequestHeadersSpec<?>> requestFactory) {
        List<String> tokenCandidates = resolveInternalTokenCandidates();
        ProxyStreamResponse lastResponse = null;

        for (int attemptIndex = 0; attemptIndex < tokenCandidates.size(); attemptIndex++) {
            lastResponse = exchangeStreamRequest(uri, requestFactory.apply(tokenCandidates.get(attemptIndex)));
            if (lastResponse.status().value() != HttpStatus.UNAUTHORIZED.value() || attemptIndex == tokenCandidates.size() - 1) {
                return lastResponse;
            }
            log.warn("AI upstream rejected primary internal token for stream uri={}; retrying with local dev fallback token.", uri);
        }

        if (lastResponse == null) {
            throw new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_EMPTY_RESPONSE", "AI 응답이 비어 있습니다.");
        }
        return lastResponse;
    }

    private ProxyStreamResponse exchangeStreamRequest(String uri, WebClient.RequestHeadersSpec<?> request) {
        long startedAtNanos = System.nanoTime();
        Integer statusCodeValue = null;
        String result = "failure";
        try {
            ResponseEntity<Flux<DataBuffer>> entityResponse = request.retrieve()
                    .toEntityFlux(DataBuffer.class)
                    .block(streamHeaderTimeout);
            if (entityResponse == null) {
                throw new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_EMPTY_RESPONSE", "AI 응답이 비어 있습니다.");
            }

            statusCodeValue = entityResponse.getStatusCode().value();
            result = resultForStatus(entityResponse.getStatusCode());
            HttpHeaders headers = filterResponseHeaders(entityResponse.getHeaders());
            Flux<DataBuffer> bodyFlux = entityResponse.getBody() != null
                    ? entityResponse.getBody()
                    : Flux.empty();
            return new ProxyStreamResponse(
                    entityResponse.getStatusCode(),
                    headers,
                    bodyFlux,
                    null);
        } catch (WebClientResponseException e) {
            statusCodeValue = e.getStatusCode().value();
            result = "upstream_error";
            log.warn("AI upstream returned status={} for stream uri={}", e.getStatusCode().value(), uri);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ProxyStreamResponse(
                    e.getStatusCode(),
                    headers,
                    Flux.empty(),
                    buildStandardizedErrorBody(e.getStatusCode()));
        } catch (WebClientRequestException e) {
            result = "connection_failure";
            throw mapRequestFailure(uri, e);
        } catch (IllegalStateException e) {
            RuntimeException mapped = mapBlockingFailure(uri, streamHeaderTimeout, e);
            result = resultForBlockingFailure(mapped);
            throw mapped;
        } finally {
            recordUpstreamRequest(uri, "stream_header", statusCodeValue, result, startedAtNanos);
        }
    }

    private WebClient client() {
        String aiServiceUrl = aiServiceSettings.getResolvedServiceUrl();
        if (!StringUtils.hasText(aiServiceUrl)) {
            log.error("AI service URL is not configured.");
            throw new AiProxyException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_URL_NOT_CONFIGURED", "AI 서비스 주소가 설정되지 않았습니다.");
        }

        WebClient localCached = cachedClient;
        if (localCached != null && aiServiceUrl.equals(cachedClientBaseUrl)) {
            return localCached;
        }

        synchronized (this) {
            if (cachedClient != null && aiServiceUrl.equals(cachedClientBaseUrl)) {
                return cachedClient;
            }
            try {
                WebClient built = webClientBuilder
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)))
                        .baseUrl(aiServiceUrl)
                        .build();
                cachedClient = built;
                cachedClientBaseUrl = aiServiceUrl;
                return built;
            } catch (IllegalArgumentException e) {
                log.error("AI service URL is invalid. url={}", aiServiceUrl, e);
                throw new AiProxyException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_URL_INVALID", "AI 서비스 주소 설정이 올바르지 않습니다.");
            }
        }
    }

    private List<String> resolveInternalTokenCandidates() {
        List<String> tokenCandidates = aiServiceSettings.getResolvedInternalTokenCandidates();
        if (!tokenCandidates.isEmpty()) {
            return tokenCandidates;
        }
        return List.of("");
    }

    private void applyInternalAuth(HttpHeaders headers, String aiInternalToken) {
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
        Throwable rootCause = rootCause(e);
        log.error(
                "AI upstream connection failed. uri={} target={} causeClass={} causeMessage={}",
                uri,
                aiServiceSettings.sanitizedServiceTarget(),
                rootCause.getClass().getName(),
                rootCause.getMessage(),
                e);
        return new AiProxyException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_CONNECTION_FAILED", "AI 서비스 연결에 실패했습니다.");
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? throwable : current;
    }

    private RuntimeException mapBlockingFailure(String uri, Duration timeout, IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
            log.error("AI upstream request timed out. uri={} timeout={}ms", uri, timeout.toMillis(), e);
            return new AiProxyException(HttpStatus.GATEWAY_TIMEOUT, "AI_UPSTREAM_TIMEOUT", "AI 응답 시간이 초과되었습니다.");
        }
        return e;
    }

    private void recordUpstreamRequest(
            String uri,
            String mode,
            Integer statusCode,
            String result,
            long startedAtNanos) {
        metricsService.recordUpstreamRequest(uri, mode, statusCode, result, System.nanoTime() - startedAtNanos);
    }

    private String resultForStatus(HttpStatusCode statusCode) {
        if (statusCode != null && statusCode.is2xxSuccessful()) {
            return "success";
        }
        return "upstream_error";
    }

    private String resultForBlockingFailure(RuntimeException exception) {
        if (exception instanceof AiProxyException aiProxyException
                && "AI_UPSTREAM_TIMEOUT".equals(aiProxyException.getCode())) {
            return "timeout";
        }
        return "failure";
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

    private boolean isClientDisconnect(IOException exception) {
        Throwable current = exception;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)
                    || "org.apache.coyote.ClientAbortException".equals(className)) {
                return true;
            }
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset by peer")
                        || normalized.contains("connection reset")) {
                    return true;
                }
            }
            current = current.getCause();
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
