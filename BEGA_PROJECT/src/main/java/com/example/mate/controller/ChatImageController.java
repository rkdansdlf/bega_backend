package com.example.mate.controller;

import com.example.auth.service.UserService;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.mate.service.ChatImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class ChatImageController {

    private final ChatImageService chatImageService;
    private final UserService userService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> uploadChatImage(@RequestPart("file") MultipartFile file, Principal principal) {
        Long userId = resolveUserId(principal);
        ChatImageService.UploadResult uploadResult = chatImageService.uploadChatImage(userId, file);
        return ResponseEntity.ok(ApiResponse.success("채팅 이미지 업로드 성공",
                Map.of("path", uploadResult.path(), "url", uploadResult.url())));
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AuthenticationRequiredException("로그인이 필요합니다.");
        }
        return userService.getUserIdByEmail(principal.getName());
    }
}
