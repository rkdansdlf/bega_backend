package com.example.auth.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.common.dto.ApiResponse;

import com.example.auth.entity.RefreshToken;
import com.example.auth.util.JWTUtil;
import com.example.auth.repository.RefreshRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class ReissueController {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    public ReissueController(JWTUtil jwtUtil, RefreshRepository refreshRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
    }

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

        // Refresh Token 추출
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("Refresh")) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        // Refresh Token이 없으면 권한 없음 처리
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Refresh Token이 없습니다."));
        }

        // Refresh Token 만료 확인
        if (jwtUtil.isExpired(refreshToken)) {
            try {
                // 토큰에서 email 가져오기
                String expiredEmail = jwtUtil.getEmail(refreshToken);
                RefreshToken expiredToken = refreshRepository.findByEmail(expiredEmail);
                if (expiredToken != null) {
                    refreshRepository.delete(expiredToken);
                }
            } catch (Exception e) {

            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Refresh Token이 만료되었습니다."));
        }

        // DB에서 Refresh Token 확인

        // 토큰에서 이메일 가져오기
        String email = jwtUtil.getEmail(refreshToken);

        // 가져온 이메일을 통해 DB에서 RefreshToken 엔티티 찾기
        RefreshToken existToken = refreshRepository.findByEmail(email);

        if (existToken == null) {
            // 해당 이메일로 등록된 Refresh Token이 DB에 없으면 띄우기
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("잘못된 Refresh Token입니다."));
        }

        // DB에 저장된 토큰 값과 요청된 토큰 값이 일치하는지 최종 확인
        if (!existToken.getToken().equals(refreshToken)) {
            // DB에 저장된 토큰이 요청된 토큰과 다르면, 모든 기존 토큰을 무효화하고 해당 사용자 로그아웃 처리 가능
            refreshRepository.delete(existToken);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Refresh Token이 일치하지 않습니다."));
        }

        // 새로운 Access Token 및 Refresh Token 생성
        String role = jwtUtil.getRole(refreshToken);

        Long userId = Long.valueOf(jwtUtil.getUserId(refreshToken));

        // Access Token 만료 시간 (2시간)
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L;

        // userId와 role의 순서를 교정함 (email, userId, role, expiredMs)
        String newAccessToken = jwtUtil.createJwt(email, role, userId, accessTokenExpiredMs);

        // userId와 role의 순서를 교정함 (email, userId, role)
        String newRefreshToken = jwtUtil.createRefreshToken(email, role, userId);

        // DB 정보 저장
        existToken.setToken(newRefreshToken);
        existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
        refreshRepository.save(existToken);

        // Access Token 쿠키
        response.addCookie(createCookie("Authorization", newAccessToken, (int) (accessTokenExpiredMs / 1000)));

        // Refresh Token 쿠키
        int refreshTokenMaxAge = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);
        response.addCookie(createCookie("Refresh", newRefreshToken, refreshTokenMaxAge));

        return ResponseEntity.ok(ApiResponse.success("토큰이 성공적으로 재발급되었습니다."));
    }

    // 쿠키 생성 헬퍼 메서드
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 로컬 개발 환경(HTTP)에서는 false로 설정

        return cookie;
    }
}
