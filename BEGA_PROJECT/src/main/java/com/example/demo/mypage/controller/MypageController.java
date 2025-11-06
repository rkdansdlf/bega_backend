package com.example.demo.mypage.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.UserEntity;
import com.example.demo.mypage.dto.UserProfileDto;
import com.example.demo.mypage.dto.MyPageUpdateDto;
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

    private static final long ACCESS_TOKEN_EXPIRED_MS = 1000 * 60 * 30; // 30분 (ms 단위)
    private final UserService userService; 
    private final JWTUtil jwtUtil; 

    //프로필 정보 조회
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse> getMyProfile(
            @AuthenticationPrincipal Long userId) {
        try {
            // JWT 토큰에서 ID (userId) 사용    
            // UserService를 통해 실제 DB에서 사용자 정보 조회
            UserEntity userEntity = userService.findUserById(userId);

            // Entity를 DTO로 변환
            UserProfileDto profileDto = UserProfileDto.builder()
                    .name(userEntity.getName())
                    .email(userEntity.getEmail()) 
                    .favoriteTeam(userEntity.getFavoriteTeamId() != null ? userEntity.getFavoriteTeamId() : "없음") 
                    .profileImageUrl(userEntity.getProfileImageUrl())
                    .createdAt(userEntity.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME)) 
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

    // 프로필 정보 수정
    @PutMapping("/mypage")
    public ResponseEntity<ApiResponse> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MyPageUpdateDto updateDto) { 
        try {
            
            // 서비스 메서드 호출 시, DTO 객체를 바로 전달
            UserEntity updatedEntity = userService.updateProfile(
                    userId,
                    updateDto // 
            );

            // 유저 정보가 수정되면 즉시 새로운 토큰 생성
            String newRoleKey = updatedEntity.getRole(); 
            String userEmail = updatedEntity.getEmail(); 
            
            String newJwtToken = jwtUtil.createJwt(userEmail, newRoleKey, ACCESS_TOKEN_EXPIRED_MS); 
            
            ResponseCookie cookie = ResponseCookie.from("Authorization", newJwtToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(ACCESS_TOKEN_EXPIRED_MS / 1000)
                    .build();

         // 토큰을 응답 데이터에 포함하여 프론트엔드가 상태 관리에 사용하도록 합니다.
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("token", newJwtToken); 
            
            // 프론트엔드 MyPage.tsx의 handleSave에서 필요한 필드들
            responseMap.put("profileImageUrl", updatedEntity.getProfileImageUrl()); // 🚨 업데이트된 URL
            responseMap.put("name", updatedEntity.getName());
            responseMap.put("email", updatedEntity.getEmail());
            responseMap.put("favoriteTeam", updatedEntity.getFavoriteTeamId() != null ? updatedEntity.getFavoriteTeamId() : "없음");

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(ApiResponse.success("프로필 수정 성공 및 JWT 쿠키 재설정 완료", responseMap));

        } catch (RuntimeException e) {
            // 유효하지 않은 팀 ID 등 RuntimeException 처리
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("프로필 수정 중 오류가 발생했습니다: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("프로필 수정 중 서버 오류가 발생했습니다."));
        }
    }
}