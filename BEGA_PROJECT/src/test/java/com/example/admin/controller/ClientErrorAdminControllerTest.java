package com.example.admin.controller;

import com.example.common.clienterror.ClientErrorAdminService;
import com.example.common.clienterror.dto.ClientErrorDashboardDto;
import com.example.common.clienterror.dto.ClientErrorDashboardTotalsDto;
import com.example.common.clienterror.dto.ClientErrorEventDetailDto;
import com.example.common.clienterror.dto.ClientErrorEventPageDto;
import com.example.common.clienterror.dto.ClientErrorEventSummaryDto;
import com.example.common.clienterror.dto.ClientErrorTimeSeriesPointDto;
import com.example.common.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientErrorAdminControllerTest {

    private MockMvc mockMvc;
    private ClientErrorAdminService clientErrorAdminService;

    @BeforeEach
    void setUp() {
        clientErrorAdminService = mock(ClientErrorAdminService.class);
        ClientErrorAdminController controller = new ClientErrorAdminController(clientErrorAdminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("클라이언트 에러 대시보드 조회 성공")
    void getDashboardSuccess() throws Exception {
        ClientErrorDashboardDto dashboard = new ClientErrorDashboardDto(
                OffsetDateTime.parse("2026-03-13T00:00:00Z"),
                OffsetDateTime.parse("2026-03-14T00:00:00Z"),
                "hour",
                new ClientErrorDashboardTotalsDto(4, 2, 1, 3, 2),
                List.of(new ClientErrorTimeSeriesPointDto(
                        OffsetDateTime.parse("2026-03-13T11:00:00Z"),
                        2,
                        1,
                        0)),
                List.of(),
                List.of(),
                List.of());

        given(clientErrorAdminService.getDashboard(any(), any())).willReturn(dashboard);

        mockMvc.perform(get("/api/admin/client-errors/dashboard")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totals.api").value(4))
                .andExpect(jsonPath("$.data.granularity").value("hour"));
    }

    @Test
    @DisplayName("클라이언트 에러 이벤트 목록 조회 성공")
    void getEventsSuccess() throws Exception {
        ClientErrorEventSummaryDto event = new ClientErrorEventSummaryDto(
                "evt-1",
                "runtime",
                "runtime",
                "render failed",
                null,
                "none",
                null,
                "/mypage",
                "/mypage",
                null,
                null,
                null,
                "fp-1",
                OffsetDateTime.parse("2026-03-13T11:00:00Z"),
                "session-1",
                99L,
                1);

        given(clientErrorAdminService.getEvents(
                eq("runtime"),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(OffsetDateTime.class),
                nullable(OffsetDateTime.class),
                eq(0),
                eq(20)))
                .willReturn(new ClientErrorEventPageDto(List.of(event), 1, 1, 20, 0, true));

        mockMvc.perform(get("/api/admin/client-errors/events")
                        .param("bucket", "runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-1"))
                .andExpect(jsonPath("$.data.content[0].bucket").value("runtime"));
    }

    @Test
    @DisplayName("클라이언트 에러 이벤트 상세 조회 성공")
    void getEventDetailSuccess() throws Exception {
        ClientErrorEventSummaryDto event = new ClientErrorEventSummaryDto(
                "evt-1",
                "api",
                "api",
                "request failed",
                500,
                "5xx",
                "INTERNAL_SERVER_ERROR",
                "/prediction",
                "/prediction",
                "GET",
                "/api/predictions",
                "/api/predictions",
                "fp-api",
                OffsetDateTime.parse("2026-03-13T11:00:00Z"),
                "session-1",
                7L,
                2);

        given(clientErrorAdminService.getEventDetail("evt-1"))
                .willReturn(new ClientErrorEventDetailDto(
                        event,
                        "stack",
                        "componentStack",
                        List.of(),
                        List.of()));

        mockMvc.perform(get("/api/admin/client-errors/events/evt-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.event.eventId").value("evt-1"))
                .andExpect(jsonPath("$.data.stack").value("stack"));
    }
}
