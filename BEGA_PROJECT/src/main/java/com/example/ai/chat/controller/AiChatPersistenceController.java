package com.example.ai.chat.controller;

import com.example.ai.chat.dto.CreateAssistantChatMessageRequest;
import com.example.ai.chat.dto.CreateUserChatMessageRequest;
import com.example.ai.chat.service.AiChatPersistenceService;
import com.example.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatPersistenceController {

    private final AiChatPersistenceService aiChatPersistenceService;

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse> listSessions(@AuthenticationPrincipal Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI 채팅 세션 목록 조회 성공",
                aiChatPersistenceService.listSessions(currentUserId)));
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse> createSession(@AuthenticationPrincipal Long currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "AI 채팅 세션 생성 성공",
                aiChatPersistenceService.createSession(currentUserId)));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse> listMessages(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI 채팅 메시지 조회 성공",
                aiChatPersistenceService.listMessages(currentUserId, sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/messages/user")
    public ResponseEntity<ApiResponse> addUserMessage(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateUserChatMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "사용자 메시지 저장 성공",
                aiChatPersistenceService.addUserMessage(currentUserId, sessionId, request)));
    }

    @PostMapping("/sessions/{sessionId}/messages/assistant")
    public ResponseEntity<ApiResponse> addAssistantMessage(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateAssistantChatMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "assistant 메시지 저장 성공",
                aiChatPersistenceService.addAssistantMessage(currentUserId, sessionId, request)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse> deleteSession(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long sessionId) {
        aiChatPersistenceService.deleteSession(currentUserId, sessionId);
        return ResponseEntity.ok(ApiResponse.success("AI 채팅 세션 삭제 성공"));
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse> listFavorites(@AuthenticationPrincipal Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI 채팅 즐겨찾기 조회 성공",
                aiChatPersistenceService.listFavorites(currentUserId)));
    }

    @PostMapping("/favorites/{messageId}")
    public ResponseEntity<ApiResponse> addFavorite(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long messageId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "AI 채팅 즐겨찾기 저장 성공",
                aiChatPersistenceService.addFavorite(currentUserId, messageId)));
    }

    @DeleteMapping("/favorites/{messageId}")
    public ResponseEntity<ApiResponse> removeFavorite(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long messageId) {
        aiChatPersistenceService.removeFavorite(currentUserId, messageId);
        return ResponseEntity.ok(ApiResponse.success("AI 채팅 즐겨찾기 삭제 성공"));
    }
}
