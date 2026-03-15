package com.example.admin.controller;

import com.example.common.clienterror.ClientErrorAdminService;
import com.example.common.clienterror.dto.ClientErrorDashboardDto;
import com.example.common.clienterror.dto.ClientErrorEventDetailDto;
import com.example.common.clienterror.dto.ClientErrorEventPageDto;
import com.example.common.dto.ApiResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class ClientErrorAdminController {

    private final ClientErrorAdminService clientErrorAdminService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        ClientErrorDashboardDto dashboard = clientErrorAdminService.getDashboard(from, to);
        return ResponseEntity.ok(ApiResponse.success("클라이언트 에러 대시보드 조회 성공", dashboard));
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse> getEvents(
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
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse> getEventDetail(@PathVariable String eventId) {
        ClientErrorEventDetailDto detail = clientErrorAdminService.getEventDetail(eventId);
        return ResponseEntity.ok(ApiResponse.success("클라이언트 에러 이벤트 상세 조회 성공", detail));
    }
}
