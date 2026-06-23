package com.example.ai.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ai.config.AiProxyRequestLimits;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AiProxyRequestLimitFilterTest {

    private AiProxyRequestLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AiProxyRequestLimitFilter(
                new AiProxyRequestLimits(8, 16, 32, 40, 24, 20),
                new ObjectMapper());
    }

    @Test
    void oversizedKnownAiProxyRequestReturns413WithoutCallingChain() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/chat/completion", 9);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains(AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE);
        assertThat(response.getContentAsString()).contains("maxBytes");
        assertThat(chain.called).isFalse();
    }

    @Test
    void underLimitKnownAiProxyRequestCallsChain() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/chat/completion", 8);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.called).isTrue();
    }

    @Test
    void unknownLengthAiProxyRequestReturns413WhenStreamReadExceedsLimit() throws Exception {
        MockHttpServletRequest request = unknownLengthRequest("POST", "/api/ai/chat/completion", 9);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ReadingFilterChain chain = new ReadingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isTrue();
        assertThat(chain.readAttempted).isTrue();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains(AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE);
        assertThat(response.getContentAsString()).contains("\"maxBytes\":8");
    }

    @Test
    void unknownLengthAiProxyRequestUnderLimitPassesAfterStreamRead() throws Exception {
        MockHttpServletRequest request = unknownLengthRequest("POST", "/api/ai/chat/completion", 8);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ReadingFilterChain chain = new ReadingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isTrue();
        assertThat(chain.readAttempted).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void contextPathIsStrippedBeforeMatchingKnownAiProxyPath() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/chat/completion", 9);
        request.setContextPath("/backend");
        request.setRequestURI("/backend/api/ai/chat/completion");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains(AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE);
        assertThat(chain.called).isFalse();
    }

    @Test
    void coachAnalyzeUsesCoachJsonLimit() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/coach/analyze", 17);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"maxBytes\":16");
        assertThat(chain.called).isFalse();
    }

    @Test
    void voiceUploadUsesRequestEnvelopeLimit() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/chat/voice", 41);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"maxBytes\":40");
        assertThat(chain.called).isFalse();
    }

    @Test
    void unknownLengthAiMultipartRequestReturns413BeforeMultipartParsing() throws Exception {
        MockHttpServletRequest request = unknownLengthRequest("POST", "/api/ai/chat/voice", 4);
        request.setContentType("multipart/form-data");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"maxBytes\":40");
        assertThat(chain.called).isFalse();
    }

    @Test
    void adminReleaseDecisionUsesAdminJsonLimit() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/release-decision/draft", 25);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"maxBytes\":24");
        assertThat(chain.called).isFalse();
    }

    @Test
    void chatPersistenceUsesPersistenceJsonLimit() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/chat/sessions/7/messages/assistant", 21);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"maxBytes\":20");
        assertThat(chain.called).isFalse();
    }

    @Test
    void unknownAiBodyEndpointDefaultsToPersistenceJsonLimit() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/new-body-endpoint", 21);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"maxBytes\":20");
        assertThat(chain.called).isFalse();
    }

    @Test
    void getAndDeleteAiPersistenceRequestsPassThroughWithoutWrapper() throws Exception {
        MockHttpServletRequest getRequest = request("GET", "/api/ai/chat/sessions", 1024);
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        TrackingFilterChain getChain = new TrackingFilterChain();

        filter.doFilter(getRequest, getResponse, getChain);

        assertThat(getResponse.getStatus()).isEqualTo(200);
        assertThat(getChain.called).isTrue();
        assertThat(getChain.request).isSameAs(getRequest);

        MockHttpServletRequest deleteRequest = request("DELETE", "/api/ai/chat/sessions/7", 1024);
        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();
        TrackingFilterChain deleteChain = new TrackingFilterChain();

        filter.doFilter(deleteRequest, deleteResponse, deleteChain);

        assertThat(deleteResponse.getStatus()).isEqualTo(200);
        assertThat(deleteChain.called).isTrue();
        assertThat(deleteChain.request).isSameAs(deleteRequest);
    }

    @Test
    void unknownNonAiPathCallsChainWithoutLimitCheckOrWrapper() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/not-ai", 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.called).isTrue();
        assertThat(chain.request).isSameAs(request);
    }

    private MockHttpServletRequest request(String method, String path, int contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setContent(new byte[contentLength]);
        return request;
    }

    private MockHttpServletRequest unknownLengthRequest(String method, String path, int contentLength) {
        MockHttpServletRequest request = new UnknownLengthMockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setContent(new byte[contentLength]);
        return request;
    }

    private static class TrackingFilterChain implements FilterChain {

        private boolean called;
        private ServletRequest request;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            called = true;
            this.request = request;
            ((MockHttpServletResponse) response).setStatus(200);
        }
    }

    private static class ReadingFilterChain implements FilterChain {

        private boolean called;
        private boolean readAttempted;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException {
            called = true;
            readAttempted = true;
            request.getInputStream().readAllBytes();
            ((MockHttpServletResponse) response).setStatus(200);
        }
    }

    private static class UnknownLengthMockHttpServletRequest extends MockHttpServletRequest {

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1L;
        }
    }
}
