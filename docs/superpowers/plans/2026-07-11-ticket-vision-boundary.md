# Ticket Vision Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move ticket-image AI transport out of the KBO domain service behind a KBO-owned capability port without changing ticket analysis behavior.

**Architecture:** `TicketAnalysisService` keeps file validation, metrics, and internal game-ID enrichment. A `TicketVisionPort` represents OCR capability, while `AiTicketVisionAdapter` owns AI URL/token resolution, multipart HTTP, canonical/legacy routing, timeout handling, and upstream error mapping.

**Tech Stack:** Java 21, Spring Boot, Spring WebFlux `WebClient`, JUnit 5, Mockito, Reactor, ArchUnit, Gradle

## Global Constraints

- Preserve `/api/tickets/analyze`, response fields, validation codes, HTTP mappings, 30-second timeout, and `/ai/vision/ticket -> /vision/ticket` 404 fallback.
- Preserve `ticket_analyze` request/reject metrics and game-ID enrichment.
- Do not change AI internal-token semantics or expose the token.
- Do not add external baseball data access or synthesize baseball facts.
- Do not stage or commit unrelated dirty-worktree changes.

---

### Task 1: Add the Ticket Transport Boundary Rule

**Files:**
- Modify: `BEGA_PROJECT/src/test/java/com/example/architecture/BackendBoundaryArchitectureTest.java`

**Interfaces:**
- Produces: architecture rule preventing `TicketAnalysisService` from importing AI config or WebClient transport types

- [ ] **Step 1: Add the failing architecture test**

Append this test to `BackendBoundaryArchitectureTest`:

```java
@Test
void ticketAnalysisServiceMustNotOwnAiTransport() {
    ArchRule rule = noClasses()
            .that().haveFullyQualifiedName("com.example.kbo.service.TicketAnalysisService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.example.ai.config..",
                    "org.springframework.web.reactive.function.client..");

    rule.check(PRODUCTION_CLASSES);
}
```

- [ ] **Step 2: Run the rule and verify RED**

```bash
./gradlew test --tests "*BackendBoundaryArchitectureTest.ticketAnalysisServiceMustNotOwnAiTransport"
```

Expected: FAIL identifying `AiServiceSettings`, `WebClient`, or `WebClientResponseException` dependencies in `TicketAnalysisService`.

### Task 2: Define the Capability Port and Characterize Domain Behavior

**Files:**
- Create: `BEGA_PROJECT/src/main/java/com/example/kbo/service/port/TicketVisionPort.java`
- Create: `BEGA_PROJECT/src/test/java/com/example/kbo/service/TicketAnalysisServiceTest.java`

**Interfaces:**
- Produces: `TicketInfo TicketVisionPort.analyze(MultipartFile file)`
- Consumes: existing `TicketInfo`, `MultipartFile`

- [ ] **Step 1: Create the KBO-owned port**

```java
package com.example.kbo.service.port;

import com.example.kbo.dto.TicketInfo;
import org.springframework.web.multipart.MultipartFile;

@FunctionalInterface
public interface TicketVisionPort {

    TicketInfo analyze(MultipartFile file);
}
```

- [ ] **Step 2: Add focused service behavior tests**

```java
package com.example.kbo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.BegaDiary.Service.BegaGameService;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.port.TicketVisionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class TicketAnalysisServiceTest {

    private final TicketVisionPort ticketVisionPort = mock(TicketVisionPort.class);
    private final BegaGameService begaGameService = mock(BegaGameService.class);
    private final StorageConfig storageConfig = mock(StorageConfig.class);
    private final ImageOptimizationMetricsService metricsService = mock(ImageOptimizationMetricsService.class);
    private final TicketAnalysisService service = new TicketAnalysisService(
            ticketVisionPort,
            begaGameService,
            storageConfig,
            metricsService);

    @BeforeEach
    void setUp() {
        when(storageConfig.getMaxImageBytes()).thenReturn(5L * 1024L * 1024L);
    }

    @Test
    void analyzeTicketDelegatesToVisionPortAndEnrichesGameId() {
        MockMultipartFile file = validImage();
        TicketInfo info = TicketInfo.builder()
                .date("2026-07-11")
                .time("18:30")
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("두산")
                .build();
        when(ticketVisionPort.analyze(file)).thenReturn(info);
        when(begaGameService.findGameIdByDateAndTeams(
                "2026-07-11", "LG", "두산", "잠실", "18:30"))
                .thenReturn(77L);

        TicketInfo result = service.analyzeTicket(file);

        assertThat(result).isSameAs(info);
        assertThat(result.getGameId()).isEqualTo(77L);
        verify(metricsService).recordRequest("ticket_analyze");
        verify(ticketVisionPort).analyze(file);
    }

    @Test
    void analyzeTicketRejectsInvalidTypeBeforeCallingVisionPort() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.gif", "image/gif", new byte[] {1});

        assertThatThrownBy(() -> service.analyzeTicket(file))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessage("지원되지 않는 이미지 형식입니다. JPG, PNG, WEBP 파일만 가능합니다.");

        verify(metricsService).recordReject("ticket_analyze", "invalid_type");
        verify(ticketVisionPort, never()).analyze(file);
    }

    private MockMultipartFile validImage() {
        return new MockMultipartFile(
                "file", "ticket.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    }
}
```

### Task 3: Move HTTP Transport into the AI Adapter

**Files:**
- Create: `BEGA_PROJECT/src/main/java/com/example/ai/adapter/AiTicketVisionAdapter.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/kbo/service/TicketAnalysisService.java`
- Create: `BEGA_PROJECT/src/test/java/com/example/ai/adapter/AiTicketVisionAdapterTest.java`

**Interfaces:**
- Implements: `TicketVisionPort.analyze(MultipartFile)`
- Consumes: `AiServiceSettings`, `WebClient.Builder`
- Produces: raw OCR `TicketInfo` or the existing `TicketAnalysisException` mapping

- [ ] **Step 1: Write adapter tests for canonical and fallback paths**

```java
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
```

- [ ] **Step 2: Implement `AiTicketVisionAdapter` by moving the existing transport unchanged**

```java
package com.example.ai.adapter;

import com.example.ai.config.AiServiceSettings;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.exception.TicketAnalysisException;
import com.example.kbo.service.port.TicketVisionPort;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiTicketVisionAdapter implements TicketVisionPort {

    private static final Duration AI_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final AiServiceSettings aiServiceSettings;
    private final WebClient.Builder webClientBuilder;

    @Override
    public TicketInfo analyze(MultipartFile file) {
        String aiServiceUrl = aiServiceSettings.getResolvedServiceUrl();
        if (!StringUtils.hasText(aiServiceUrl)) {
            log.error("AI service URL is not configured for ticket analysis.");
            throw new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 주소가 설정되지 않았습니다.");
        }

        String aiInternalToken = aiServiceSettings.getResolvedInternalToken();
        if (!StringUtils.hasText(aiInternalToken)) {
            log.error("ai.internal-token is not configured; cannot call ticket analysis endpoint.");
            throw new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 인증 설정이 누락되었습니다. 관리자에게 문의해주세요.");
        }

        try {
            byte[] imageBytes = file.getBytes();
            String fileName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "ticket.jpg";
            WebClient client = webClientBuilder
                    .baseUrl(Objects.requireNonNull(aiServiceUrl))
                    .build();

            try {
                return analyzeWithUri(client, imageBytes, fileName, "/ai/vision/ticket", aiInternalToken);
            } catch (WebClientResponseException exception) {
                if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                    log.warn("Primary AI ticket endpoint not found, fallback to legacy path. cause={}",
                            exception.getMessage());
                    return analyzeWithUri(client, imageBytes, fileName, "/vision/ticket", aiInternalToken);
                }
                throw mapUpstreamException(exception);
            }
        } catch (IOException exception) {
            log.error("Failed to read ticket image", exception);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "티켓 이미지를 읽는 중 오류가 발생했습니다.");
        } catch (WebClientResponseException exception) {
            log.error("AI Service returned error response. status={}",
                    exception.getStatusCode().value(), exception);
            throw mapUpstreamException(exception);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("Timeout on blocking read")) {
                log.error("AI Service call timed out after {} seconds",
                        AI_REQUEST_TIMEOUT.toSeconds(), exception);
                throw new TicketAnalysisException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "티켓 분석 요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
            }
            log.error("AI Service call failed", exception);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 서비스를 호출하지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (TicketAnalysisException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("AI Service call failed", exception);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private TicketInfo analyzeWithUri(
            WebClient client,
            byte[] imageBytes,
            String filename,
            String uri,
            String aiInternalToken) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        TicketInfo info = client.post()
                .uri(uri)
                .header("X-Internal-Api-Key", aiInternalToken)
                .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA))
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(TicketInfo.class)
                .block(AI_REQUEST_TIMEOUT);

        if (info == null) {
            throw new RuntimeException("AI Service returned empty response");
        }
        return info;
    }

    private TicketAnalysisException mapUpstreamException(WebClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        if (statusCode == HttpStatus.UNAUTHORIZED.value()
                || statusCode == HttpStatus.FORBIDDEN.value()) {
            return new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 인증에 실패했습니다. 서버 설정을 확인해주세요.");
        }
        if (statusCode == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일 크기가 너무 큽니다. (최대 5MB)");
        }
        if (statusCode == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "지원되지 않는 이미지 형식입니다. JPG, PNG, WEBP 파일만 가능합니다.");
        }
        if (statusCode >= 400 && statusCode < 500) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "티켓 분석 요청이 올바르지 않습니다. 다른 파일로 다시 시도해주세요.");
        }
        if (statusCode >= 500) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 AI 서버에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
        return new TicketAnalysisException(
                HttpStatus.BAD_GATEWAY,
                "티켓 분석 서비스 호출에 실패했습니다.");
    }
}
```

- [ ] **Step 3: Reduce `TicketAnalysisService` to validation, delegation, and enrichment**

Its dependencies become:

```java
private final TicketVisionPort ticketVisionPort;
private final BegaGameService begaGameService;
private final StorageConfig storageConfig;
private final ImageOptimizationMetricsService metricsService;
```

The method becomes:

```java
public TicketInfo analyzeTicket(MultipartFile file) {
    log.info("Analyzing ticket image: {}", file != null ? file.getOriginalFilename() : null);
    metricsService.recordRequest("ticket_analyze");
    validateTicketImage(file);
    return enrichTicketInfoWithGameId(ticketVisionPort.analyze(file));
}
```

Remove all AI settings, HTTP, timeout, multipart-body, and upstream exception mapping imports and methods from this service. Keep `validateTicketImage` and `enrichTicketInfoWithGameId` behavior unchanged.

- [ ] **Step 4: Run focused tests and verify GREEN**

```bash
./gradlew test \
  --tests "*TicketAnalysisServiceTest" \
  --tests "*AiTicketVisionAdapterTest" \
  --tests "*BackendBoundaryArchitectureTest.ticketAnalysisServiceMustNotOwnAiTransport" \
  --tests "*TicketControllerTest"
```

Expected: PASS.

- [ ] **Step 5: Run boundary regression tests**

```bash
./gradlew test --tests "*Ticket*Test" --tests "*AiServiceSettingsTest"
```

Expected: PASS.

- [ ] **Step 6: Inspect and commit only this slice**

```bash
git diff --check -- \
  BEGA_PROJECT/src/main/java/com/example/kbo/service \
  BEGA_PROJECT/src/main/java/com/example/ai/adapter/AiTicketVisionAdapter.java \
  BEGA_PROJECT/src/test/java/com/example/kbo/service/TicketAnalysisServiceTest.java \
  BEGA_PROJECT/src/test/java/com/example/ai/adapter/AiTicketVisionAdapterTest.java \
  BEGA_PROJECT/src/test/java/com/example/architecture/BackendBoundaryArchitectureTest.java
```

Stage only the listed files and commit:

```bash
git commit -m "refactor: isolate ticket vision transport"
```

## Plan Self-Review

- The port is capability-specific and owned by the consuming KBO domain.
- The adapter owns all transport and legacy routing details.
- Validation, metrics, game matching, public controller behavior, and error messages remain covered.
- The architecture rule prevents transport logic from returning to `TicketAnalysisService`.
- No database, external baseball data, Frontend, FastAPI, auth, or public contract change is included.
