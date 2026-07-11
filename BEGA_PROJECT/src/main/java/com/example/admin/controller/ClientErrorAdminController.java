package com.example.admin.controller;

import com.example.common.clienterror.ClientErrorAdminService;
import com.example.common.clienterror.dto.ClientErrorDashboardDto;
import com.example.common.clienterror.dto.ClientErrorEventDetailDto;
import com.example.common.clienterror.dto.ClientErrorEventPageDto;
import com.example.common.dto.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/client-errors")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class ClientErrorAdminController {

    private static final String PAGE_SIZE_NOT_APPLICABLE = "not_applicable";

    private final ClientErrorAdminService clientErrorAdminService;
    private final MeterRegistry meterRegistry;

    public ClientErrorAdminController(ClientErrorAdminService clientErrorAdminService) {
        this(clientErrorAdminService, Metrics.globalRegistry);
    }

    @Autowired
    public ClientErrorAdminController(ClientErrorAdminService clientErrorAdminService, MeterRegistry meterRegistry) {
        this.clientErrorAdminService = clientErrorAdminService;
        this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<ClientErrorDashboardDto>> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        long startedAtNanos = System.nanoTime();
        String result = "success";
        int statusCode = 200;
        try {
            ClientErrorDashboardDto dashboard = clientErrorAdminService.getDashboard(from, to);
            return ResponseEntity.ok(ApiResponse.success("클라이언트 에러 대시보드 조회 성공", dashboard));
        } catch (RuntimeException e) {
            result = resolveResult(e);
            statusCode = resolveStatusCode(e);
            throw e;
        } finally {
            recordRequestDuration("dashboard", result, statusCode, PAGE_SIZE_NOT_APPLICABLE, startedAtNanos);
        }
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<ClientErrorEventPageDto>> getEvents(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String statusGroup,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String fingerprint,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        long startedAtNanos = System.nanoTime();
        String result = "success";
        int statusCode = 200;
        String pageSizeBucket = resolvePageSizeBucket(size);
        try {
            ClientErrorEventPageDto events = clientErrorAdminService.getEvents(
                    bucket,
                    source,
                    statusGroup,
                    route,
                    fingerprint,
                    search,
                    from,
                    to,
                    page,
                    size);
            return ResponseEntity.ok(ApiResponse.success("클라이언트 에러 이벤트 조회 성공", events));
        } catch (RuntimeException e) {
            result = resolveResult(e);
            statusCode = resolveStatusCode(e);
            throw e;
        } finally {
            recordRequestDuration("events", result, statusCode, pageSizeBucket, startedAtNanos);
        }
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<ClientErrorEventDetailDto>> getEventDetail(@PathVariable String eventId) {
        long startedAtNanos = System.nanoTime();
        String result = "success";
        int statusCode = 200;
        try {
            ClientErrorEventDetailDto detail = clientErrorAdminService.getEventDetail(eventId);
            return ResponseEntity.ok(ApiResponse.success("클라이언트 에러 이벤트 상세 조회 성공", detail));
        } catch (RuntimeException e) {
            result = resolveResult(e);
            statusCode = resolveStatusCode(e);
            throw e;
        } finally {
            recordRequestDuration("detail", result, statusCode, PAGE_SIZE_NOT_APPLICABLE, startedAtNanos);
        }
    }

    private void recordRequestDuration(
            String endpoint,
            String result,
            int statusCode,
            String pageSizeBucket,
            long startedAtNanos) {
        long durationNanos = System.nanoTime() - startedAtNanos;
        if (durationNanos < 0) {
            return;
        }

        Timer.builder("client_error_admin_request_duration_seconds")
                .description("Client error admin request duration")
                .publishPercentileHistogram()
                .tags(
                        "endpoint", normalizeMetricTag(endpoint),
                        "result", normalizeMetricTag(result),
                        "status_group", normalizeStatusGroup(statusCode),
                        "page_size", normalizeMetricTag(pageSizeBucket))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String resolveResult(RuntimeException exception) {
        if (exception instanceof NoSuchElementException) {
            return "not_found";
        }
        if (exception instanceof IllegalArgumentException) {
            return "bad_request";
        }
        return "failed";
    }

    private int resolveStatusCode(RuntimeException exception) {
        if (exception instanceof NoSuchElementException) {
            return 404;
        }
        if (exception instanceof IllegalArgumentException) {
            return 400;
        }
        return 500;
    }

    private String resolvePageSizeBucket(int size) {
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        if (normalizedSize <= 20) {
            return "1_20";
        }
        if (normalizedSize <= 50) {
            return "21_50";
        }
        return "51_100";
    }

    private String normalizeMetricTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private String normalizeStatusGroup(int statusCode) {
        if (statusCode < 100) {
            return "unknown";
        }
        return (statusCode / 100) + "xx";
    }
}
