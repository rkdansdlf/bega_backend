package com.example.demo.mypage.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.UserEntity;
import com.example.demo.mypage.dto.UserProfileDto;
import com.example.demo.service.UserService;
import com.example.demo.jwt.JWTUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal; 
import org.springframework.http.HttpHeaders; 
import org.springframework.http.ResponseCookie; 

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import jakarta.validation.Valid;



//마이페이지 기능을 위한 컨트롤러입니다.
@RestController
@RequestMapping("/api/auth") 
@RequiredArgsConstructor
public class MypageController {

    // ⭐ 추가: JWT Access Token 만료 시간 (30분)
    private static final long ACCESS_TOKEN_EXPIRED_MS = 1000 * 60 * 30; // 30분 (ms 단위)

    private final UserService userService; 
    private final JWTUtil jwtUtil; 

//[GET] 프로필 정보 조회 API
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse> getMyProfile(
            @AuthenticationPrincipal Long userId) {
        try {
            // 1. JWT 토큰에서 추출된 ID (userId) 사용    
            // 2. UserService를 통해 실제 DB에서 사용자 정보 조회
            UserEntity userEntity = userService.findUserById(userId);

            // 3. Entity를 DTO로 변환
            UserProfileDto profileDto = UserProfileDto.builder()
                    .name(userEntity.getName())
                    .email(userEntity.getEmail()) 
                    .favoriteTeam(userEntity.getFavoriteTeamId() != null ? userEntity.getFavoriteTeamId() : "없음") 
                    .profileImageUrl(userEntity.getProfileImageUrl())
                    .createdAt(userEntity.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME)) // 👈 수정된 부분
                    .role(userEntity.getRole()) 
                    .build();

            // 성공 응답 (HTTP 200 OK)
            return ResponseEntity.ok(ApiResponse.success("프로필 조회 성공", profileDto));

        } catch (RuntimeException e) {
            System.err.println("프로필 조회 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("요청한 사용자의 프로필 정보를 찾을 수 없습니다."));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("프로필 정보를 불러오는 중 서버 오류가 발생했습니다."));
        }
    }

    /**
     * [PUT] 프로필 정보 수정 API
     * PUT /api/auth/mypage
     */
    @PutMapping("/mypage")
    public ResponseEntity<ApiResponse> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileDto updateDto) {
        try {
            if (updateDto.getName() == null || updateDto.getName().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("이름/닉네임은 필수 입력 항목입니다."));
            }

            UserEntity updatedEntity = userService.updateProfile(
                    userId,
                    updateDto.getName(),
                    updateDto.getProfileImageUrl(),
                    updateDto.getFavoriteTeam() != null && !updateDto.getFavoriteTeam().equals("없음") ? 
                        updateDto.getFavoriteTeam() : null
            );

            UserProfileDto updatedProfile = UserProfileDto.builder()
                    .name(updatedEntity.getName()) 
                    .email(updatedEntity.getEmail())
                    .favoriteTeam(updatedEntity.getFavoriteTeamId() != null ? updatedEntity.getFavoriteTeamId() : "없음")
                    .profileImageUrl(updatedEntity.getProfileImageUrl())
                    .createdAt(updatedEntity.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                    .build();

            String newRoleKey = updatedEntity.getRole(); 
            String userEmail = updatedEntity.getEmail(); 
            
            String newJwtToken = jwtUtil.createJwt(userEmail, newRoleKey, ACCESS_TOKEN_EXPIRED_MS); 
            
            ResponseCookie cookie = ResponseCookie.from("Authorization", newJwtToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(ACCESS_TOKEN_EXPIRED_MS / 1000)
                    .build();

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("profile", updatedProfile); 

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(ApiResponse.success("프로필 수정 성공 및 JWT 쿠키 재설정 완료", responseData));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("프로필 수정 중 오류가 발생했습니다: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("프로필 수정 중 서버 오류가 발생했습니다."));
        }
    }
}
