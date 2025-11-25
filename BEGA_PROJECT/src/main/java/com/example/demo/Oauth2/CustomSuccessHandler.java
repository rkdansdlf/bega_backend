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

        // 1. Principal에서 사용자 정보 추출
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        
        // 이메일 추출
        String userEmail = (String) principal.getAttributes().get("email"); 
        
        if (userEmail == null || userEmail.isEmpty()) {
            userEmail = principal.getUsername(); 
        }
        
        if (userEmail == null || userEmail.isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response, "/oauth2/login/error?message=email_missing");
            return;
        }
        
        // DB에서 UserEntity 조회
        Optional<UserEntity> userEntityOptional = userRepository.findByEmail(userEmail); 
        
        if (userEntityOptional.isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response, "/oauth2/login/error?message=user_not_found_in_db");
            return;
        }

        UserEntity userEntity = userEntityOptional.get();
        
        //UserEntity에서 Role 가져오기 
        String role = userEntity.getRole(); 
        
        // DB에 저장된 사용자의 이름(name)을 리다이렉션에 사용
        String userName = userEntity.getName();

        Long userId = userEntity.getId();
        // Access Token 생성
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; // 2시간
        String accessToken = jwtUtil.createJwt(userEmail, role, userId, accessTokenExpiredMs); 

        // Refresh Token 생성 
        String refreshToken = jwtUtil.createRefreshToken(userEmail, role, userId); 

        // Refresh Token DB 저장 또는 업데이트
        RefreshToken existToken = refreshRepository.findByEmail(userEmail); 

        if (existToken == null) {
            RefreshToken newRefreshToken = new RefreshToken();
            newRefreshToken.setEmail(userEmail); 
            newRefreshToken.setToken(refreshToken);
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); //만료시간 1주일
            refreshRepository.save(newRefreshToken);

        } else {
            // 기존 토큰 업데이트
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }

        // 쿠키에 Access/Refresh Token 동시 추가 (LoginFilter와 동일한 메서드 사용)
        int accessTokenMaxAge = (int)(accessTokenExpiredMs / 1000);
        addSameSiteCookie(response, "Authorization", accessToken, accessTokenMaxAge);
        
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000); 
        addSameSiteCookie(response, "Refresh", refreshToken, refreshTokenMaxAge);
            
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        
        // 7. 리다이렉션 
        String encodedUsername = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        String redirectUrl = "http://localhost:3000"; // 쿠키를 추가한 후 리다이렉트
        
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
    
 
    private void addSameSiteCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        // SameSite=Lax는 보안은 유지하면서 로컬 환경(HTTP)에서도 잘 작동하도록 합니다.
        // SameSite=None을 사용하려면 반드시 Secure 속성이 필요하고, HTTPS 환경이어야 합니다.
        String cookieString = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax", 
                                            name, value, maxAgeSeconds);
        response.addHeader("Set-Cookie", cookieString);
    }
}