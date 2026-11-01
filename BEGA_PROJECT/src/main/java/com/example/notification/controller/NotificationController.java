package com.example.notification.controller;

import com.example.common.exception.BadRequestBusinessException;
import com.example.notification.dto.NotificationDTO;
import com.example.notification.dto.MarkAllAsReadRequest;
import com.example.notification.dto.MarkAllAsReadResponse;
import com.example.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_NOTIFICATION_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    // 내 알림 목록 조회 (page/size 미지정 시 최근 30건만 반환)
    @GetMapping("/my")
    @Operation(summary = "내 알림을 페이지 단위로 조회합니다.", parameters = {
            @Parameter(name = "size", in = ParameterIn.QUERY,
                    description = "기본 30건입니다. 숫자 100 초과는 거부하며 0 이하 또는 숫자가 아닌 값은 기본값을 사용합니다.", example = "30",
                    schema = @Schema(type = "integer", defaultValue = "30", maximum = "100"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "알림 목록",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NotificationDTO.Response.class)))),
            @ApiResponse(responseCode = "400", description = "페이지 크기가 허용 범위를 벗어났습니다.",
                    content = @Content(schema = @Schema(implementation = com.example.common.dto.ApiResponse.class)))
    })
    public ResponseEntity<List<NotificationDTO.Response>> getMyNotifications(
            @AuthenticationPrincipal Long userId,
            @ParameterObject @PageableDefault(size = NotificationService.DEFAULT_NOTIFICATION_PAGE_SIZE)
            Pageable pageable,
            HttpServletRequest request) {
        String requestedSize = request.getParameter("size");
        if (requestedSize != null && exceedsMaximumPageSize(requestedSize)) {
            throw new BadRequestBusinessException(
                    "NOTIFICATION_PAGE_SIZE_EXCEEDED",
                    "알림 페이지 크기는 100건 이하여야 합니다.");
        }

        List<NotificationDTO.Response> notifications = notificationService.getMyNotifications(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    private static boolean exceedsMaximumPageSize(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int index = 0;
        boolean negative = value.charAt(0) == '-';
        if (negative || value.charAt(0) == '+') {
            index++;
        }
        if (index == value.length()) {
            return false;
        }

        int parsed = 0;
        boolean exceeded = false;
        for (; index < value.length(); index++) {
            int digit = Character.digit(value.charAt(index), 10);
            if (digit < 0) {
                return false;
            }
            if (!exceeded) {
                parsed = parsed * 10 + digit;
                exceeded = parsed > MAX_NOTIFICATION_PAGE_SIZE;
            }
        }
        return !negative && exceeded;
    }

    // 내 읽지 않은 알림 개수
    @GetMapping("/my/unread-count")
    public ResponseEntity<Long> getMyUnreadCount(@AuthenticationPrincipal Long userId) {
        Long count = notificationService.getMyUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    // 알림 읽음 처리
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId, @AuthenticationPrincipal Long userId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    // 전체 알림 일괄 읽음 처리
    @PostMapping("/mark-all-read")
    public ResponseEntity<MarkAllAsReadResponse> markAllAsRead(
            @Valid @RequestBody MarkAllAsReadRequest request,
            @AuthenticationPrincipal Long userId) {
        List<Long> notificationIds = notificationService.markAllAsRead(
                userId, request.notificationIds());
        return ResponseEntity.ok(new MarkAllAsReadResponse(notificationIds));
    }

    // 알림 삭제
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long notificationId, @AuthenticationPrincipal Long userId) {
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.noContent().build();
    }
}
