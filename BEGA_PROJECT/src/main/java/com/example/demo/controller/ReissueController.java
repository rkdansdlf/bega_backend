package com.example.demo.controller;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.entity.RefreshToken;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class ReissueController {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    public ReissueController(JWTUtil jwtUtil, RefreshRepository refreshRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
    }

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

        // 1. 요청에서 Refresh Token 추출
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

        // 1-1. Refresh Token이 없으면 권한 없음 처리
        if (refreshToken == null) {
            return new ResponseEntity<>("refresh token null", HttpStatus.BAD_REQUEST);
        }

        // 2. Refresh Token 만료 확인
        if (jwtUtil.isExpired(refreshToken)) {
            return new ResponseEntity<>("refresh token expired", HttpStatus.BAD_REQUEST);
        }

        // 3. 토큰 종류 확인 (Refresh 토큰인지 확인하는 클레임 검사 필요 시 추가 가능)

        // 4. DB에서 Refresh Token 검증 (토큰 존재 여부 확인)
        // Refresh Token 자체가 DB에 저장된 값과 일치하는지 확인합니다.
        RefreshToken existToken = refreshRepository.findByToken(refreshToken);

        if (existToken == null) {
            // DB에 저장된 적 없는 토큰 (변조 또는 유효하지 않은 토큰)
            return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
        }
        
        // 5. 새로운 Access Token 및 Refresh Token 생성
        String username = jwtUtil.getUsername(refreshToken);
        String role = jwtUtil.getRole(refreshToken); // 👈 JWTUtil의 getRole() 사용
        
        // Access Token 만료 시간 (예: 2시간)
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; 
        String newAccessToken = jwtUtil.createJwt(username, role, accessTokenExpiredMs); // 👈 role 포함하여 생성
        
        // Refresh Token Rotating: 기존 Refresh Token을 폐기하고 새로운 Refresh Token 발행
        String newRefreshToken = jwtUtil.createRefreshToken(username, role); // 👈 role 포함하여 생성

        // 6. DB 정보 업데이트 (기존 토큰 폐기 및 새 토큰 저장)
        existToken.setToken(newRefreshToken);
        existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
        refreshRepository.save(existToken);

        // 7. 클라이언트에 새 토큰 응답 (쿠키로 전송)
        
        // 7-1. 새 Access Token 쿠키
        response.addCookie(createCookie("Authorization", newAccessToken, (int)(accessTokenExpiredMs / 1000)));
        
        // 7-2. 새 Refresh Token 쿠키
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000);
        response.addCookie(createCookie("Refresh", newRefreshToken, refreshTokenMaxAge));
        
        System.out.println("--- Token Reissue Success ---");
        System.out.println("Username: " + username + " -> Tokens Renewed");
        System.out.println("-----------------------------");

        return new ResponseEntity<>("Token reissued successfully", HttpStatus.OK);
    }

    // 쿠키 생성 헬퍼 메서드
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); // HTTPS 환경에서 사용 권장

        return cookie;
    }
}
