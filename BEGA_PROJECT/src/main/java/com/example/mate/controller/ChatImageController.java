package com.example.mate.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.ratelimit.RateLimit;
import com.example.mate.dto.ChatImageDTO;
import com.example.mate.service.ChatImageService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class ChatImageController {

    private final ChatImageService chatImageService;

    @RateLimit(limit = 20, window = 60, key = "image:chat")
    @Operation(summary = "Mate chat image upload")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatImageDTO.UploadResponse>> uploadChatImage(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Long userId) {
        ChatImageService.UploadResult uploadResult = chatImageService.uploadChatImage(requireUserId(userId), file);
        ChatImageDTO.UploadResponse response = ChatImageDTO.UploadResponse.builder()
                .path(uploadResult.path())
                .url(uploadResult.url())
                .build();
        return ResponseEntity.ok(ApiResponse.success("채팅 이미지 업로드 성공",
                response));
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("로그인이 필요합니다.");
        }
        return userId;
    }
}
