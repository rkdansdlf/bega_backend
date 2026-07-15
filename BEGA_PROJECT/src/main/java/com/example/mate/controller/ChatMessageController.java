package com.example.mate.controller;

import com.example.common.web.AuthenticatedUserIds;
import com.example.mate.dto.ChatMessageDTO;
import com.example.mate.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Validated
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    // 메시지 전송
    @Operation(summary = "Mate chat message send")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = ChatMessageDTO.Response.class)))
    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDTO.Response> sendMessage(
            @Valid @RequestBody ChatMessageDTO.Request request,
            @AuthenticationPrincipal Long userId) {
        ChatMessageDTO.Response response = chatMessageService.sendMessage(request, AuthenticatedUserIds.require(userId));
        messagingTemplate.convertAndSend("/topic/party/" + response.getPartyId(), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파티별 채팅 메시지 조회
    @Operation(summary = "Mate chat messages by party")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChatMessageDTO.Response.class))))
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<ChatMessageDTO.Response>> getMessagesByPartyId(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit,
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @RequestParam(required = false) @Positive Long beforeId) {
        List<ChatMessageDTO.Response> messages = chatMessageService.getMessagesByPartyId(
                partyId,
                AuthenticatedUserIds.require(userId),
                limit,
                beforeId);
        return ResponseEntity.ok(messages);
    }

    // 기존 직접 호출 호환용 오버로드 (HTTP 매핑 없음)
    public ResponseEntity<List<ChatMessageDTO.Response>> getMessagesByPartyId(
            Long partyId,
            Long userId) {
        List<ChatMessageDTO.Response> messages = chatMessageService.getMessagesByPartyId(
                partyId,
                AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(messages);
    }

    // 파티별 최근 메시지 조회
    @Operation(summary = "Mate latest chat message by party")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OK",
                    content = @Content(schema = @Schema(implementation = ChatMessageDTO.Response.class))),
            @ApiResponse(responseCode = "204", description = "No Content", content = @Content)
    })
    @GetMapping("/party/{partyId}/latest")
    public ResponseEntity<ChatMessageDTO.Response> getLatestMessage(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        ChatMessageDTO.Response message = chatMessageService.getLatestMessage(partyId, AuthenticatedUserIds.require(userId));
        if (message != null) {
            return ResponseEntity.ok(message);
        }
        return ResponseEntity.noContent().build();
    }
}
