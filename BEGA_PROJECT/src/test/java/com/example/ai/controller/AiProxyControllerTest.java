package com.example.ai.controller;

import com.example.ai.exception.AiProxyException;
import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.ai.service.AiProxyService.ProxyStreamResponse;
import com.example.common.exception.GlobalExceptionHandler;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiProxyControllerTest {

    private MockMvc mockMvc;
    private AiProxyService aiProxyService;

    @BeforeEach
    void setup() {
        aiProxyService = mock(AiProxyService.class);
        AiProxyController controller = new AiProxyController(aiProxyService);
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
                .andExpect(jsonPath("$.message").value("AI 응답 시간이 초과되었습니다."));
    }
}
