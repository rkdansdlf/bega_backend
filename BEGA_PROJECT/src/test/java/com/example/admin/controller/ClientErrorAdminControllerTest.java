package com.example.admin.controller;

import com.example.common.clienterror.ClientErrorAdminService;
import com.example.common.clienterror.dto.ClientErrorAlertNotificationDto;
import com.example.common.clienterror.dto.ClientErrorDashboardDto;
import com.example.common.clienterror.dto.ClientErrorDashboardTotalsDto;
import com.example.common.clienterror.dto.ClientErrorEventDetailDto;
import com.example.common.clienterror.dto.ClientErrorEventPageDto;
import com.example.common.clienterror.dto.ClientErrorEventSummaryDto;
import com.example.common.clienterror.dto.ClientErrorTimeSeriesPointDto;
import com.example.common.exception.GlobalExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        clientErrorAdminService = mock(ClientErrorAdminService.class);
        meterRegistry = new SimpleMeterRegistry();
        ClientErrorAdminController controller = new ClientErrorAdminController(clientErrorAdminService, meterRegistry);
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
                List.of(new ClientErrorAlertNotificationDto(
                        1L,
                        "fp-runtime",
                        "runtime",
                        "runtime",
                        "telegram",
                        "/mypage",
                        "none",
                        3,
                        3,
                        5,
                        "evt-3",
                        "render failed",
                        OffsetDateTime.parse("2026-03-13T11:00:00Z"),
                        OffsetDateTime.parse("2026-03-13T11:01:00Z"),
                        "SENT",
                        null)));

        given(clientErrorAdminService.getDashboard(any(), any())).willReturn(dashboard);

        mockMvc.perform(get("/api/admin/client-errors/dashboard")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totals.api").value(4))
                .andExpect(jsonPath("$.data.granularity").value("hour"))
                .andExpect(jsonPath("$.data.recentAlerts[0].channel").value("telegram"));

        assertTimerCount("dashboard", "success", "2xx", "not_applicable", 1);
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
                eq(40)))
                .willReturn(new ClientErrorEventPageDto(List.of(event), 1, 1, 40, 0, true));

        mockMvc.perform(get("/api/admin/client-errors/events")
                        .param("bucket", "runtime")
                        .param("size", "40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-1"))
                .andExpect(jsonPath("$.data.content[0].bucket").value("runtime"));

        assertTimerCount("events", "success", "2xx", "21_50", 1);
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

        assertTimerCount("detail", "success", "2xx", "not_applicable", 1);
    }

    @Test
    @DisplayName("클라이언트 에러 이벤트 상세 조회 실패도 메트릭으로 기록한다")
    void getEventDetailNotFoundRecordsMetric() throws Exception {
        given(clientErrorAdminService.getEventDetail("missing"))
                .willThrow(new NoSuchElementException("not found"));

        mockMvc.perform(get("/api/admin/client-errors/events/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertTimerCount("detail", "not_found", "4xx", "not_applicable", 1);
    }

    private void assertTimerCount(
            String endpoint,
            String result,
            String statusGroup,
            String pageSize,
            long expectedCount) {
        Timer timer = meterRegistry.find("client_error_admin_request_duration_seconds")
                .tag("endpoint", endpoint)
                .tag("result", result)
                .tag("status_group", statusGroup)
                .tag("page_size", pageSize)
                .timer();
        assertNotNull(timer);
        assertEquals(expectedCount, timer.count());
    }
}
