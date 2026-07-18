package com.example.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiStreamHttpErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesExactCanonicalSnakeCaseShape() throws Exception {
        var response = new AiStreamHttpErrorResponse(
                "AI_EVENT_VERSION_UNSUPPORTED",
                "지원하지 않는 AI 이벤트 버전입니다.",
                null,
                false,
                null,
                List.of("1", "2"));

        assertThat(objectMapper.readTree(objectMapper.writeValueAsBytes(response)))
                .isEqualTo(objectMapper.readTree("""
                        {
                          "code":"AI_EVENT_VERSION_UNSUPPORTED",
                          "message":"지원하지 않는 AI 이벤트 버전입니다.",
                          "detail":null,
                          "retryable":false,
                          "retry_after_seconds":null,
                          "supported_versions":["1","2"]
                        }
                        """));
    }
}
