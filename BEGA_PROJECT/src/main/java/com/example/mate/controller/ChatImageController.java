package com.example.mate.controller;

import com.example.auth.service.UserService;
import com.example.common.dto.ApiResponse;
import com.example.mate.service.ChatImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("로그인이 필요합니다."));
        }

        try {
            Long userId = userService.getUserIdByEmail(principal.getName());
            ChatImageService.UploadResult uploadResult = chatImageService.uploadChatImage(userId, file);
            return ResponseEntity.ok(ApiResponse.success("채팅 이미지 업로드 성공",
                    Map.of("path", uploadResult.path(), "url", uploadResult.url())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("채팅 이미지 업로드에 실패했습니다."));
        }
    }
}
