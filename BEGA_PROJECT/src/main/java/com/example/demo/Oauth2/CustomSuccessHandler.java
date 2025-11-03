package com.example.demo.Oauth2;

import com.example.demo.dto.CustomOAuth2User;
import com.example.demo.entity.RefreshToken;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional; 
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository; 

    public CustomSuccessHandler(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 1. Principal에서 사용자 정보 (CustomOAuth2User) 추출
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        
        // 🚨 핵심 수정: 이메일 추출 로직을 OAuth2 Attributes와 CustomOAuth2User.getUsername() 모두에서 시도하여
        // Google, Kakao 등 모든 제공자에 대해 안전하게 이메일을 추출하도록 개선합니다.
        String userEmail = (String) principal.getAttributes().get("email"); 
        
        if (userEmail == null || userEmail.isEmpty()) {
            // Attributes에서 이메일이 바로 추출되지 않을 경우, CustomOAuth2UserService에서 설정한 username (UserDto)을 시도합니다.
            // 이 username은 CustomOAuth2UserService에서 이메일로 설정되었어야 합니다.
            userEmail = principal.getUsername(); 
        }
        
        if (userEmail == null || userEmail.isEmpty()) {
            System.err.println("🚨 CustomSuccessHandler: Google/Kakao attributes 및 UserDto에서 유효한 email을 찾을 수 없습니다. (이메일 동의 필요 또는 데이터 누락)");
            // 이메일이 없는 경우, 에러 페이지로 리다이렉트
            getRedirectStrategy().sendRedirect(request, response, "/oauth2/login/error?message=email_missing");
            return;
        }
        
        // 2. DB에서 UserEntity 조회 (Optional 사용)
        Optional<UserEntity> userEntityOptional = userRepository.findByEmail(userEmail); 
        
        if (userEntityOptional.isEmpty()) {
            // 이메일로 사용자를 찾을 수 없는 경우 (CustomOAuth2UserService에서 이미 저장/업데이트 되었어야 함)
            System.err.println("🚨 CustomSuccessHandler: DB에서 이메일(" + userEmail + ")로 사용자를 찾을 수 없습니다. (DB 저장 실패 가능성)");
            // 이 경우 로그인 실패로 간주하고 에러 페이지로 리다이렉트
            getRedirectStrategy().sendRedirect(request, response, "/oauth2/login/error?message=user_not_found_in_db");
            return;
        }

        UserEntity userEntity = userEntityOptional.get();
        
        // 🚨 핵심: UserEntity에서 단일 Role String을 가져옴
        String role = userEntity.getRole(); 
        
        // DB에 저장된 사용자의 이름(name)을 리다이렉션에 사용
        String userName = userEntity.getName();

        // 3. Access Token 생성
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; // 2시간
        String accessToken = jwtUtil.createJwt(userEmail, role, accessTokenExpiredMs); 

        // 4. Refresh Token 생성 (만료 시간은 JWTUtil에 정의됨)
        String refreshToken = jwtUtil.createRefreshToken(userEmail, role); 

        // 5. Refresh Token DB 저장 또는 업데이트
        // ⭐️ 수정: findByUsername 대신 findByEmail 사용
        RefreshToken existToken = refreshRepository.findByEmail(userEmail); 

        if (existToken == null) {
            RefreshToken newRefreshToken = new RefreshToken();
            // ⭐️ 수정: setUsername 대신 setEmail 사용
            newRefreshToken.setEmail(userEmail); 
            newRefreshToken.setToken(refreshToken);
            // 만료 시간을 1주로 설정 (JWTUtil의 리프레시 토큰 만료 시간과 일치시킬 필요가 있음)
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); 
            refreshRepository.save(newRefreshToken);

        } else {
            // 기존 토큰 업데이트
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }

        // 6. 클라이언트에 토큰 응답 (쿠키)
        
        // Access Token 쿠키
        response.addCookie(createCookie("Authorization", accessToken, (int)(accessTokenExpiredMs / 1000)));
        
        // Refresh Token 쿠키
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000); 
        response.addCookie(createCookie("Refresh", refreshToken, refreshTokenMaxAge));
        
        // 7. 리다이렉션 (프론트엔드로 사용자 이름과 역할을 쿼리 파라미터로 전달)
        String encodedUsername = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        String redirectUrl = "http://localhost:3000";
        
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);

        System.out.println("--- JWT 토큰 발행 성공 (OAuth2 로그인) ---");
        System.out.println("발행된 Access Token: " + accessToken.substring(0, 10) + "...");
        System.out.println("Refresh Token (DB 저장됨): " + refreshToken.substring(0, 10) + "...");
        System.out.println("토큰 사용자(Email): " + userEmail); 
        System.out.println("권한: " + role);
        System.out.println("-------------------------------------");
    }
    
    // 쿠키 생성 헬퍼 메서드
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); // HTTPS 환경에서만 사용

        return cookie;
    }
}

