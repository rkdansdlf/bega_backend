package com.example.common.clienterror;

import com.example.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientErrorControllerTest {

    private MockMvc mockMvc;
    private ClientErrorLoggingService clientErrorLoggingService;

    @BeforeEach
    void setUp() {
        clientErrorLoggingService = mock(ClientErrorLoggingService.class);
        ClientErrorController controller = new ClientErrorController(clientErrorLoggingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("클라이언트 에러 로깅 실패 시에도 202를 반환한다")
    void ingestClientErrorReturnsAcceptedWhenLoggingFails() throws Exception {
        doThrow(new RuntimeException("metrics unavailable"))
                .when(clientErrorLoggingService)
                .logClientError(any(), any());

        mockMvc.perform(post("/api/client-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-1",
                                  "category": "api",
                                  "message": "Request failed",
                                  "statusCode": 500,
                                  "route": "/mypage",
                                  "timestamp": "2026-03-13T11:00:00Z"
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("클라이언트 피드백 로깅 실패 시에도 202를 반환한다")
    void ingestClientErrorFeedbackReturnsAcceptedWhenLoggingFails() throws Exception {
        doThrow(new RuntimeException("metrics unavailable"))
                .when(clientErrorLoggingService)
                .logClientFeedback(any(), any());

        mockMvc.perform(post("/api/client-errors/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-1",
                                  "comment": "retry helped",
                                  "actionTaken": "retry",
                                  "route": "/mypage",
                                  "timestamp": "2026-03-13T11:00:00Z"
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("클라이언트 에러 요청 validation 실패는 표준 에러 응답을 반환한다")
    void ingestClientErrorValidationFailureReturnsStandardizedError() throws Exception {
        mockMvc.perform(post("/api/client-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "",
                                  "category": "",
                                  "message": "",
                                  "route": "",
                                  "timestamp": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.eventId").exists())
                .andExpect(jsonPath("$.errors.category").exists())
                .andExpect(jsonPath("$.errors.message").exists())
                .andExpect(jsonPath("$.errors.route").exists())
                .andExpect(jsonPath("$.errors.timestamp").exists());
    }
}
