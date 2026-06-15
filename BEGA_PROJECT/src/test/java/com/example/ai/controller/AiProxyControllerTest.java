package com.example.ai.controller;

import com.example.ai.config.AiProxyRequestLimits;
import com.example.ai.exception.AiProxyException;
import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.ai.service.AiProxyService.ProxyStreamResponse;
import com.example.ai.service.CoachAutoBriefMonitoringService;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.ratelimit.RateLimit;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiProxyControllerTest {

    private MockMvc mockMvc;
    private AiProxyService aiProxyService;
    private CoachAutoBriefMonitoringService coachAutoBriefMonitoringService;

    @BeforeEach
    void setup() {
        aiProxyService = mock(AiProxyService.class);
        coachAutoBriefMonitoringService = mock(CoachAutoBriefMonitoringService.class);
        AiProxyController controller = new AiProxyController(
                aiProxyService,
                coachAutoBriefMonitoringService,
                new AiProxyRequestLimits(4096, 4096, 1024 * 1024, 1024 * 1024 + 65536, 256 * 1024, 128 * 1024));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("release decision presets 프록시 성공")
    void releaseDecisionPresetsSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        given(aiProxyService.forwardGet(eq("/ai/release-decision/presets")))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.OK,
                        headers,
                        "[{\"scenario\":\"prediction_stage2\"}]".getBytes()));

        mockMvc.perform(get("/api/ai/release-decision/presets"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"scenario\":\"prediction_stage2\"}]"));
    }

    @Test
    @DisplayName("release decision eval cases 프록시 성공")
    void releaseDecisionEvalCasesSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        given(aiProxyService.forwardGet(eq("/ai/release-decision/eval-cases")))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.OK,
                        headers,
                        "[{\"case_id\":\"prediction_stage2_hold\"}]".getBytes()));

        mockMvc.perform(get("/api/ai/release-decision/eval-cases"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"case_id\":\"prediction_stage2_hold\"}]"));
    }

    @Test
    @DisplayName("release decision artifacts 프록시 성공")
    void releaseDecisionArtifactsSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        given(aiProxyService.forwardGet(eq("/ai/release-decision/artifacts")))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.OK,
                        headers,
                        "[{\"artifact_id\":\"artifact_1\"}]".getBytes()));

        mockMvc.perform(get("/api/ai/release-decision/artifacts"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"artifact_id\":\"artifact_1\"}]"));
    }

    @Test
    @DisplayName("release decision artifact detail 프록시 성공")
    void releaseDecisionArtifactDetailSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        given(aiProxyService.forwardGet(eq("/ai/release-decision/artifacts/artifact_1")))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.OK,
                        headers,
                        "{\"artifact_id\":\"artifact_1\"}".getBytes()));

        mockMvc.perform(get("/api/ai/release-decision/artifacts/artifact_1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"artifact_id\":\"artifact_1\"}"));
    }

    @Test
    @DisplayName("release decision draft 프록시 성공")
    void releaseDecisionDraftSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"scenario\":\"prediction_stage2\"}";
        String responseBody = "{\"result\":{\"scenario\":\"prediction_stage2\"}}";

        given(aiProxyService.forwardJson(eq("/ai/release-decision/draft"), eq(payload)))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.OK,
                        headers,
                        responseBody.getBytes()));

        mockMvc.perform(post("/api/ai/release-decision/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json(responseBody));
    }

    @Test
    @DisplayName("release decision evaluate 프록시 성공")
    void releaseDecisionEvaluateSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"case_id\":\"prediction_stage2_hold\"}";
        String responseBody = "{\"evaluation\":{\"status\":\"PASS\"}}";

        given(aiProxyService.forwardJson(eq("/ai/release-decision/evaluate"), eq(payload)))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.OK,
                        headers,
                        responseBody.getBytes()));

        mockMvc.perform(post("/api/ai/release-decision/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json(responseBody));
    }

    @Test
    @DisplayName("release decision save 프록시 성공")
    void releaseDecisionSaveSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"scenario\":\"prediction_stage2\"}";
        String responseBody = "{\"artifact_id\":\"artifact_1\"}";

        given(aiProxyService.forwardJson(eq("/ai/release-decision/save"), eq(payload)))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.OK,
                        headers,
                        responseBody.getBytes()));

        mockMvc.perform(post("/api/ai/release-decision/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json(responseBody));
    }

    @Test
    @DisplayName("chat stream 프록시 성공 시 SSE를 스트리밍한다")
    void chatStreamSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.add("X-Accel-Buffering", "no");
        String payload = "{\"question\":\"테스트\"}";
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

        given(aiProxyService.forwardJsonStream(eq("/ai/chat/stream"), eq(payload)))
                .willReturn(new ProxyStreamResponse(
                        HttpStatus.OK,
                        headers,
                        Flux.just(
                                bufferFactory.wrap("event: message\ndata: {\"delta\":\"안녕\"}\n\n".getBytes(StandardCharsets.UTF_8)),
                                bufferFactory.wrap("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8))),
                        null));
        willAnswer(invocation -> {
            OutputStream outputStream = invocation.getArgument(1);
            outputStream.write("event: message\ndata: {\"delta\":\"안녕\"}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).given(aiProxyService).writeStream(any(), any());

        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().string(containsString("event: message")))
                .andExpect(content().string(containsString("data: [DONE]")));
    }

    @Test
    @DisplayName("chat stream upstream 오류는 표준 JSON 에러 응답으로 변환된다")
    void chatStreamUpstreamFailureReturnsStandardizedErrorResponse() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"question\":\"테스트\"}";
        String responseBody = "{\"success\":false,\"code\":\"AI_UPSTREAM_UNAVAILABLE\",\"message\":\"AI 서비스가 현재 사용할 수 없습니다.\"}";

        given(aiProxyService.forwardJsonStream(eq("/ai/chat/stream"), eq(payload)))
                .willReturn(new ProxyStreamResponse(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        headers,
                        Flux.empty(),
                        responseBody.getBytes(StandardCharsets.UTF_8)));

        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json(responseBody));
    }

    @Test
    @DisplayName("coach analyze 프록시 성공 시 SSE를 스트리밍한다")
    void coachAnalyzeSuccess() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.add("X-Accel-Buffering", "no");
        String payload = "{\"home_team_id\":\"HH\",\"away_team_id\":\"SS\",\"request_mode\":\"manual_detail\",\"analysis_type\":\"game_review\"}";
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        given(coachAutoBriefMonitoringService.extractRequestMode(eq(payload))).willReturn("manual_detail");
        given(coachAutoBriefMonitoringService.extractAnalysisType(eq(payload))).willReturn("game_review");

        given(aiProxyService.forwardJsonStream(eq("/ai/coach/analyze"), eq(payload)))
                .willReturn(new ProxyStreamResponse(
                        HttpStatus.OK,
                        headers,
                        Flux.just(
                                bufferFactory.wrap("event: meta\ndata: {\"request_mode\":\"manual_detail\"}\n\n".getBytes(StandardCharsets.UTF_8)),
                                bufferFactory.wrap("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8))),
                        null));
        willAnswer(invocation -> {
            OutputStream outputStream = invocation.getArgument(1);
            outputStream.write("event: meta\ndata: {\"request_mode\":\"manual_detail\"}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).given(aiProxyService).writeStream(any(), any());

        MvcResult result = mockMvc.perform(post("/api/ai/coach/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().string(containsString("event: meta")))
                .andExpect(content().string(containsString("data: [DONE]")));

        verify(coachAutoBriefMonitoringService).recordCoachAnalyzeDuration(eq("manual_detail"), eq("game_review"), eq(200), anyLong());
    }

    @Test
    @DisplayName("coach analyze upstream 오류는 표준 JSON 에러 응답으로 변환된다")
    void coachAnalyzeUpstreamFailureReturnsStandardizedErrorResponse() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"home_team_id\":\"HH\",\"away_team_id\":\"SS\",\"request_mode\":\"manual_detail\",\"analysis_type\":\"game_review\"}";
        String responseBody = "{\"success\":false,\"code\":\"AI_UPSTREAM_UNAVAILABLE\",\"message\":\"AI 서비스가 현재 사용할 수 없습니다.\"}";
        given(coachAutoBriefMonitoringService.extractRequestMode(eq(payload))).willReturn("manual_detail");
        given(coachAutoBriefMonitoringService.extractAnalysisType(eq(payload))).willReturn("game_review");

        given(aiProxyService.forwardJsonStream(eq("/ai/coach/analyze"), eq(payload)))
                .willReturn(new ProxyStreamResponse(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        headers,
                        Flux.empty(),
                        responseBody.getBytes(StandardCharsets.UTF_8)));

        MvcResult result = mockMvc.perform(post("/api/ai/coach/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json(responseBody));

        verify(coachAutoBriefMonitoringService).recordCoachAnalyzeDuration(eq("manual_detail"), eq("game_review"), eq(503), anyLong());
    }

    @Test
    @DisplayName("AI 프록시 예외는 표준 에러 응답으로 변환된다")
    void chatCompletionProxyFailureReturnsStandardizedErrorResponse() throws Exception {
        String payload = "{\"question\":\"테스트\"}";
        given(aiProxyService.forwardJson(eq("/ai/chat/completion"), eq(payload)))
                .willThrow(new AiProxyException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI_UPSTREAM_TIMEOUT",
                        "AI 응답 시간이 초과되었습니다."));

        mockMvc.perform(post("/api/ai/chat/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AI_UPSTREAM_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    @Test
    @DisplayName("chat completion payload가 backend 한도를 넘으면 upstream 호출 전 413을 반환한다")
    void chatCompletionOversizedPayloadReturns413BeforeProxyForwarding() throws Exception {
        MockMvc limitedMockMvc = mockMvcWithLimits(
                new AiProxyRequestLimits(8, 4096, 1024 * 1024, 1024 * 1024 + 65536, 256 * 1024, 128 * 1024));

        limitedMockMvc.perform(post("/api/ai/chat/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"too long\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE));

        verify(aiProxyService, never()).forwardJson(any(), any());
    }

    @Test
    @DisplayName("coach analyze payload가 backend 한도를 넘으면 upstream 호출 전 413을 반환한다")
    void coachAnalyzeOversizedPayloadReturns413BeforeProxyForwarding() throws Exception {
        MockMvc limitedMockMvc = mockMvcWithLimits(
                new AiProxyRequestLimits(4096, 8, 1024 * 1024, 1024 * 1024 + 65536, 256 * 1024, 128 * 1024));

        limitedMockMvc.perform(post("/api/ai/coach/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"home_team_id\":\"HH\",\"away_team_id\":\"SS\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE));

        verify(aiProxyService, never()).forwardJsonStream(any(), any());
    }

    @Test
    @DisplayName("voice upload가 backend 한도를 넘으면 upstream 호출 전 413을 반환한다")
    void chatVoiceOversizedUploadReturns413BeforeProxyForwarding() throws Exception {
        MockMvc limitedMockMvc = mockMvcWithLimits(new AiProxyRequestLimits(4096, 4096, 4, 1024, 256 * 1024, 128 * 1024));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.wav",
                "audio/wav",
                new byte[] {1, 2, 3, 4, 5});

        limitedMockMvc.perform(multipart("/api/ai/chat/voice").file(file))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE));

        verify(aiProxyService, never()).forwardMultipart(any(), any());
    }

    @Test
    @DisplayName("release decision admin payload가 backend 한도를 넘으면 upstream 호출 전 413을 반환한다")
    void releaseDecisionDraftOversizedPayloadReturns413BeforeProxyForwarding() throws Exception {
        MockMvc limitedMockMvc = mockMvcWithLimits(new AiProxyRequestLimits(4096, 4096, 1024 * 1024, 1024 * 1024, 8, 128 * 1024));

        limitedMockMvc.perform(post("/api/ai/release-decision/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"prediction_stage2\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE));

        verify(aiProxyService, never()).forwardJson(eq("/ai/release-decision/draft"), any());
    }

    @Test
    @DisplayName("AI 프록시 공개 사용자 엔드포인트는 fail-closed rate limit을 사용한다")
    void aiProxyUserEndpointsHaveFailClosedRateLimits() throws Exception {
        assertRateLimit("chatCompletion", new Class<?>[] {String.class}, 60, "ai:chat");
        assertRateLimit("chatStream", new Class<?>[] {String.class}, 60, "ai:chat");
        assertRateLimit(
                "chatVoice",
                new Class<?>[] {org.springframework.web.multipart.MultipartFile.class},
                20,
                "ai:chat_voice");
        assertRateLimit("coachAnalyze", new Class<?>[] {String.class}, 25, "ai:coach");
    }

    private MockMvc mockMvcWithLimits(AiProxyRequestLimits requestLimits) {
        AiProxyController controller = new AiProxyController(
                aiProxyService,
                coachAutoBriefMonitoringService,
                requestLimits);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void assertRateLimit(String methodName, Class<?>[] parameterTypes, int expectedLimit, String expectedKey)
            throws Exception {
        Method method = AiProxyController.class.getMethod(methodName, parameterTypes);
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertThat(rateLimit).isNotNull();
        assertThat(rateLimit.limit()).isEqualTo(expectedLimit);
        assertThat(rateLimit.window()).isEqualTo(60);
        assertThat(rateLimit.key()).isEqualTo(expectedKey);
        assertThat(rateLimit.failClosed()).isTrue();
    }
}
