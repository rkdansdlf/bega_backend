package com.example.ai.filter;

import com.example.ai.config.AiProxyRequestLimits;
import com.example.ai.exception.AiProxyPayloadTooLargeException;
import com.example.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AiProxyRequestLimitFilter extends OncePerRequestFilter {

    private final AiProxyRequestLimits requestLimits;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Long maxBytes = requestLimits.maxBytesFor(request.getMethod(), resolveApplicationPath(request));
        long contentLength = request.getContentLengthLong();
        if (maxBytes != null && contentLength > maxBytes) {
            writePayloadTooLarge(response, maxBytes);
            return;
        }
        // Multipart parsing can consume parts before controller code sees the wrapped stream.
        // Unknown-length AI multipart requests are fail-closed unless the client sends Content-Length.
        if (maxBytes != null && contentLength < 0 && isMultipart(request)) {
            writePayloadTooLarge(response, maxBytes);
            return;
        }

        if (maxBytes == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            filterChain.doFilter(new LimitedBodyHttpServletRequest(request, maxBytes), response);
        } catch (AiProxyPayloadTooLargeException e) {
            if (response.isCommitted()) {
                throw e;
            }
            writePayloadTooLarge(response, e.getMaxBytes());
        } catch (ServletException e) {
            AiProxyPayloadTooLargeException payloadTooLarge = findPayloadTooLarge(e);
            if (payloadTooLarge == null || response.isCommitted()) {
                throw e;
            }
            writePayloadTooLarge(response, payloadTooLarge.getMaxBytes());
        }
    }

    private String resolveApplicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null) {
            return null;
        }

        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            String path = requestUri.substring(contextPath.length());
            return path.isEmpty() ? "/" : path;
        }

        return requestUri;
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/");
    }

    private void writePayloadTooLarge(HttpServletResponse response, long maxBytes) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE,
                "AI 요청 본문이 너무 큽니다.",
                Map.of("maxBytes", maxBytes)));
    }

    private AiProxyPayloadTooLargeException findPayloadTooLarge(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AiProxyPayloadTooLargeException payloadTooLarge) {
                return payloadTooLarge;
            }
            current = current.getCause();
        }
        return null;
    }

    private static class LimitedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream inputStream;
        private BufferedReader reader;

        LimitedBodyHttpServletRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) {
                throw new IllegalStateException("getReader() has already been called for this request");
            }
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                if (inputStream != null) {
                    throw new IllegalStateException("getInputStream() has already been called for this request");
                }
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
                reader = new BufferedReader(new InputStreamReader(inputStream, resolveCharset()));
            }
            return reader;
        }

        private Charset resolveCharset() {
            String encoding = getCharacterEncoding();
            if (encoding == null || encoding.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            return Charset.forName(encoding);
        }
    }

    private static class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead;

        LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                recordBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                recordBytes(read);
            }
            return read;
        }

        @Override
        public int readLine(byte[] b, int off, int len) throws IOException {
            int read = delegate.readLine(b, off, len);
            if (read > 0) {
                recordBytes(read);
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void recordBytes(long bytes) {
            bytesRead += bytes;
            if (bytesRead > maxBytes) {
                throw new AiProxyPayloadTooLargeException(maxBytes);
            }
        }
    }
}
