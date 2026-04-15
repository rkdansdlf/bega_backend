package com.example.dm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.common.dto.ApiResponse;
import com.example.dm.dto.DmMessageDto;
import com.example.dm.dto.DmRoomDto;
import com.example.dm.service.DmMessageService;
import com.example.dm.service.DmRoomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DmController {

    private final DmRoomService dmRoomService;
    private final DmMessageService dmMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse> bootstrapRoom(
            @AuthenticationPrincipal Long currentUserId,
            @Valid @RequestBody DmRoomDto.BootstrapRequest request) {
        DmRoomDto.BootstrapResponse response = dmRoomService.bootstrapRoom(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success("DM 대화방을 준비했습니다.", response));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse> getMessages(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long roomId) {
        List<DmMessageDto.Response> response = dmMessageService.getMessages(roomId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("DM 메시지 조회 성공", response));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse> sendMessage(
            @AuthenticationPrincipal Long currentUserId,
            @Valid @RequestBody DmMessageDto.Request request) {
        DmMessageDto.Response response = dmMessageService.sendMessage(currentUserId, request);
        messagingTemplate.convertAndSend("/topic/dm/" + response.getRoomId(), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("DM 메시지 전송 성공", response));
    }
}
